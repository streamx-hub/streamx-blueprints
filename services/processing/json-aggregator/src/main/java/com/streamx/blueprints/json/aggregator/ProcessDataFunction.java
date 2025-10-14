package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import com.streamx.blueprints.json.aggregator.stores.DataStore;
import com.streamx.blueprints.json.aggregator.stores.PreservedData;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
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
  public DataStore getStore() {
    return store;
  }

  @Override
  protected boolean requiresHashInKey() {
    return false;
  }

  @Incoming(Channels.DATA)
  @Outgoing(Channels.AGGREGATED_DATA)
  Multi<Message<CloudEvent>> process(Message<CloudEvent> message) {
    return processDataMessage(message);
  }

  @Override
  protected Optional<Message<CloudEvent>> createMessageForConfig(Configuration config,
      Data data, DataKey key, String eventType, OffsetDateTime eventTime) {
    String masterNamespace = config.masterNamespace();
    PreservedData masterResource = determineMasterResource(masterNamespace, data, key,
        eventType);
    if (masterResource != null || Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return handleResourceMerging(eventTime, key, masterResource, config, eventType);
    } else {
      log.tracef("No master resource present for [%s]. Skipping processing.", key.id());
      return Optional.empty();
    }
  }

  private PreservedData determineMasterResource(String masterNamespace, Data data, DataKey key,
      String eventType) {
    return masterNamespace.equals(key.namespace())
        ? new PreservedData(data, eventType)
        : store.get(DataKey.fromNamespaceAndId(masterNamespace, key.id()));
  }

  private Optional<Message<CloudEvent>> handleResourceMerging(OffsetDateTime eventTime,
      DataKey key, PreservedData masterResource, Configuration config, String eventType) {
    if (Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return unmergeResources(eventTime, key, masterResource, config);
    }
    return Optional.of(mergeResources(eventTime, key.id(),
        supportedNamespacesByConfig.get(config),
        config.outputNamespace(), getOutputType(config, masterResource)));
  }

  private Optional<Message<CloudEvent>> unmergeResources(OffsetDateTime eventTime, DataKey key,
      PreservedData masterResource, Configuration config) {
    if (masterResource == null || masterResource.data() == null) {
      if (!key.namespace().equals(config.masterNamespace())) {
        log.tracef("Not updating master resource since it's not available at [%s]", key);
        return Optional.empty();
      } else {
        log.tracef("Unpublishing master resource at [%s]", key);
        return Optional.of(createUnpublishMessage(key.id(), eventTime, config.outputNamespace()));
      }
    } else {
      log.tracef("Unpublishing optional resource at [%s]", key);
      Set<String> namespacesToMerge = new LinkedHashSet<>(supportedNamespacesByConfig.get(config));
      namespacesToMerge.remove(key.namespace());
      return Optional.of(mergeResources(eventTime, key.id(), namespacesToMerge,
          config.outputNamespace(), getOutputType(config, masterResource)));
    }
  }

  private Message<CloudEvent> mergeResources(OffsetDateTime eventTime, String id,
      Set<String> namespacesToMerge, String outputNamespace, String outputType) {
    List<Data> resourcesToMerge = namespacesToMerge.stream()
        .map(namespace -> store.get(DataKey.fromNamespaceAndId(namespace, id)))
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
