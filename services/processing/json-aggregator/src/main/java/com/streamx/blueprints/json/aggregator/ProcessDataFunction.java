package com.streamx.blueprints.json.aggregator;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessDataFunction extends AbstractFunction {

  @Inject
  @FromChannel(CHANNEL_DATA)
  Store<Data> store;

  @Inject
  Logger log;

  @Incoming(CHANNEL_DATA)
  @Outgoing(CHANNEL_AGGREGATED_DATA)
  Multi<Message<Data>> process(Message<Data> message) {
    String key = extractKey(message);
    log.tracef("Processing message [%s]", key);
    Action action = extractAction(message);
    Long eventTime = extractEventTime(message);
    try {
      if (!accept(key, action, eventTime)) {
        log.tracef("Skipping invalid incoming message key=%s", key);
        message.ack();
        return Multi.createFrom().empty();
      }

      List<Message<Data>> resultMessages = new LinkedList<>();
      String[] keyParts = key.split(KEY_SEPARATOR);
      String id = keyParts[ID_POSITION];
      String namespace = keyParts[NAMESPACE_POSITION];
      List<Configuration> matchingConfigurations = getConfigurations(namespace);
      for (Configuration config : matchingConfigurations) {
        String masterNamespace = config.masterNamespace();
        GenericPayload<Data> masterResource = masterNamespace.equals(namespace)
            ? GenericPayload.from(message)
            : store.getWithMetadata(masterNamespace + KEY_SEPARATOR + id);
        if (masterResource != null || Action.UNPUBLISH.equals(action)) {
          handleResourceMerging(eventTime, id, namespace, masterResource, config, action, key)
              .ifPresent(resultMessages::add);
        } else {
          log.tracef("No master resource present for [%s]. Skipping processing.", id);
        }
      }
      return Multi.createFrom().items(resultMessages.stream())
          .onCompletion()
          .call(() -> Uni.createFrom().completionStage(message.ack()));
    } catch (Exception e) {
      message.nack(e);
      return Multi.createFrom().empty();
    }
  }

  private boolean accept(String key, Action action, Long eventTime) {
    return super.accept(key) && action != null && eventTime != null
        && key.split(KEY_SEPARATOR).length == 2;
  }

  private Optional<Message<Data>> handleResourceMerging(long eventTime, String id,
      String namespace, GenericPayload<Data> masterResource, Configuration config, Action action,
      String key) {
    if (Action.UNPUBLISH.equals(action)) {
      return unmergeResources(eventTime, key, id, namespace, masterResource, config);
    }
    return Optional.of(mergeResources(eventTime, id, supportedNamespacesByConfig.get(config),
        config.outputNamespace(), getOutputType(config, masterResource)));
  }

  private Optional<Message<Data>> unmergeResources(long eventTime, String key,
      String id, String namespace, GenericPayload<Data> masterResource, Configuration config) {
    if (masterResource == null || masterResource.getPayload() == null) {
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

  private Message<Data> mergeResources(long eventTime, String id, Set<String> namespacesToMerge,
      String outputNamespace, String outputType) {
    List<Data> resourcesToMerge = namespacesToMerge.stream()
        .map(namespace -> store.getWithMetadata(namespace + KEY_SEPARATOR + id))
        .filter(Objects::nonNull)
        .filter(resource -> !Action.UNPUBLISH.equals(Action.from(resource.getMetadata())))
        .map(GenericPayload::getPayload)
        .toList();
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode merged = mapper.createObjectNode();
      for (Data resource : resourcesToMerge) {
        JsonNode jsonToMerge = mapper.readTree(resource.getContentAsString());
        merged = mapper.readerForUpdating(merged).readTree(jsonToMerge.toString());
      }
      String mergedJson = merged.toString();
      log.tracef("Merged json for [%s] %s", id, mergedJson);
      return createPublishMessage(id, eventTime, outputNamespace, outputType, mergedJson);
    } catch (IOException e) {
      throw new RuntimeException("Unable to deserialize payload", e);
    }
  }

  private String getOutputType(Configuration config, GenericPayload<Data> masterResource) {
    return config.outputType().orElse(
        Optional.ofNullable(masterResource)
            .map(Properties::from)
            .flatMap(Properties::getType)
            .orElse(null));
  }
}
