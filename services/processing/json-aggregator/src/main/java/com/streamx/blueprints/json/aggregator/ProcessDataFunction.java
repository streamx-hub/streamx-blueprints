package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import com.streamx.blueprints.json.aggregator.stores.DataStore;
import com.streamx.blueprints.json.aggregator.stores.PreservedData;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class ProcessDataFunction extends AbstractFunction {

  @Inject
  DataStore store;

  @Override
  protected int expectedKeyPartsCount() {
    return 2;
  }

  @Incoming(Channels.DATA)
  @Outgoing(Channels.AGGREGATED_DATA)
  Multi<Message<CloudEvent>> process(Message<CloudEvent> message) {
    CloudEvent event = message.getPayload();
    Data data = CloudEventUtils.getData(event, Data.class);
    String eventType = event.getType();
    String key = CloudEventUtils.getSubject(event);
    OffsetDateTime eventTime = event.getTime();

    store.register(data, eventType, key);
    log.tracef("Processing message [%s]", key);

    try {
      if (!accept(key, eventTime)) {
        log.tracef("Skipping invalid incoming message key=%s", key);
        message.ack();
        return Multi.createFrom().empty();
      }

      List<Message<CloudEvent>> resultMessages = new LinkedList<>();
      String[] keyParts = key.split(KEY_SEPARATOR);
      String id = keyParts[ID_POSITION];
      String namespace = keyParts[NAMESPACE_POSITION];
      List<Configuration> matchingConfigurations = getConfigurations(namespace);
      for (Configuration config : matchingConfigurations) {
        String masterNamespace = config.masterNamespace();
        PreservedData masterResource = determineMasterResource(masterNamespace, data, namespace, id,
            eventType);
        if (masterResource != null || Data.TYPE_UNPUBLISHED.equals(eventType)) {
          handleResourceMerging(eventTime, id, namespace, masterResource, config, eventType, key)
              .ifPresent(resultMessages::add);
        } else {
          log.tracef("No master resource present for [%s]. Skipping processing.", id);
        }
      }
      return Multi.createFrom().iterable(resultMessages)
          .onCompletion()
          .call(() -> Uni.createFrom().completionStage(message.ack()));
    } catch (Exception e) {
      message.nack(e);
      return Multi.createFrom().empty();
    }
  }

  private PreservedData determineMasterResource(String masterNamespace, Data data, String namespace,
      String id, String eventType) {
    return masterNamespace.equals(namespace)
        ? new PreservedData(data, eventType)
        : store.get(masterNamespace + KEY_SEPARATOR + id);
  }

  private Optional<Message<CloudEvent>> handleResourceMerging(OffsetDateTime eventTime, String id,
      String namespace, PreservedData masterResource, Configuration config, String eventType,
      String key) {
    if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return unmergeResources(eventTime, key, id, namespace, masterResource, config);
    }
    return Optional.of(mergeResources(eventTime, id, supportedNamespacesByConfig.get(config),
        config.outputNamespace(), getOutputType(config, masterResource)));
  }

  private Optional<Message<CloudEvent>> unmergeResources(OffsetDateTime eventTime, String key,
      String id, String namespace, PreservedData masterResource, Configuration config) {
    if (masterResource == null || masterResource.data() == null) {
      if (!namespace.equals(config.masterNamespace())) {
        log.tracef("Not updating master resource since it's not available at [%s]", key);
        return Optional.empty();
      } else {
        log.tracef("Unpublishing master resource at [%s]", key);
        return Optional.of(createUnpublishMessage(id, eventTime, config.outputNamespace()));
      }
    } else {
      log.tracef("Unpublishing optional resource at [%s]", key);
      Set<String> namespacesToMerge = new LinkedHashSet<>(supportedNamespacesByConfig.get(config));
      namespacesToMerge.remove(namespace);
      return Optional.of(mergeResources(eventTime, id, namespacesToMerge, config.outputNamespace(),
          getOutputType(config, masterResource)));
    }
  }

  private Message<CloudEvent> mergeResources(OffsetDateTime eventTime, String id,
      Set<String> namespacesToMerge, String outputNamespace, String outputType) {
    List<Data> resourcesToMerge = namespacesToMerge.stream()
        .map(namespace -> store.get(namespace + KEY_SEPARATOR + id))
        .filter(Objects::nonNull)
        .filter(resource -> !Data.TYPE_UNPUBLISHED.equals(resource.eventType()))
        .map(PreservedData::data)
        .toList();
    try {
      JsonNode merged = objectMapper.createObjectNode();
      for (Data resource : resourcesToMerge) {
        JsonNode jsonToMerge = objectMapper.readTree(resource.getContentAsString());
        merged = objectMapper.readerForUpdating(merged).readTree(jsonToMerge.toString());
      }
      String mergedJson = merged.toString();
      log.tracef("Merged json for [%s] %s", id, mergedJson);
      return createPublishMessage(id, eventTime, outputNamespace, outputType, mergedJson);
    } catch (IOException e) {
      throw new RuntimeException("Unable to deserialize payload", e);
    }
  }

  private String getOutputType(Configuration config, PreservedData masterResource) {
    return config.outputType().orElse(
        Optional.ofNullable(masterResource)
            .map(PreservedData::data)
            .map(Data::getType)
            .orElse(null));
  }
}
