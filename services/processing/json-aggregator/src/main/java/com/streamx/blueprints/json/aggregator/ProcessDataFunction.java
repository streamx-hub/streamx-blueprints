package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.AggregationConfiguration;
import com.streamx.blueprints.json.aggregator.stores.DataStore;
import com.streamx.blueprints.json.aggregator.stores.PreservedData;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class ProcessDataFunction extends BaseProcessingFunction {

  @Inject
  DataStore store;

  @Override
  protected boolean requiresHashInKey() {
    return false;
  }

  @Incoming(Channels.DATA)
  @Outgoing(Channels.AGGREGATED_DATA)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  Multi<CloudEvent> process(CloudEvent event) {
    return processDataEvent(event);
  }

  @Override
  protected Optional<CloudEvent> createEventForConfig(AggregationConfiguration config,
      CloudEvent inputEvent, Data data, DataKey key) {
    String masterNamespace = config.masterNamespace();
    String eventType = inputEvent.getType();
    PreservedData masterResource = determineMasterResource(masterNamespace, data, key, eventType);
    if (masterResource != null || Data.TYPE_UNPUBLISHED.equals(eventType)) {
      return handleResourceMerging(inputEvent, key, masterResource, config);
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

  private Optional<CloudEvent> handleResourceMerging(CloudEvent inputEvent, DataKey key,
      PreservedData masterResource, AggregationConfiguration config) {
    if (Data.TYPE_UNPUBLISHED.equals(inputEvent.getType())) {
      return unmergeResources(inputEvent, key, masterResource, config);
    }
    return Optional.of(mergeResources(inputEvent, key.id(), getNamespacesByConfig(config),
        config.outputNamespace(), getOutputType(config, masterResource)));
  }

  private Optional<CloudEvent> unmergeResources(CloudEvent inputEvent, DataKey key,
      PreservedData masterResource, AggregationConfiguration config) {
    if (masterResource == null || masterResource.data() == null) {
      if (!key.namespace().equals(config.masterNamespace())) {
        log.tracef("Not updating master resource since it's not available at [%s]", key);
        return Optional.empty();
      } else {
        log.tracef("Unpublishing master resource at [%s]", key);
        return Optional.of(createUnpublishEvent(inputEvent, key.id(), config.outputNamespace()));
      }
    } else {
      log.tracef("Unpublishing optional resource at [%s]", key);
      Set<String> namespacesToMerge = new LinkedHashSet<>(getNamespacesByConfig(config));
      namespacesToMerge.remove(key.namespace());
      return Optional.of(mergeResources(inputEvent, key.id(), namespacesToMerge,
          config.outputNamespace(), getOutputType(config, masterResource)));
    }
  }

  private CloudEvent mergeResources(CloudEvent inputEvent, String id,
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
      return createPublishEvent(inputEvent, id, outputNamespace, outputType, mergedJson);
    } catch (IOException e) {
      throw new RuntimeException("Unable to deserialize payload", e);
    }
  }

  private String getOutputType(AggregationConfiguration config, PreservedData masterResource) {
    return config.outputType().orElse(
        Optional.ofNullable(masterResource)
            .map(PreservedData::data)
            .map(Data::getType)
            .orElse(null));
  }
}
