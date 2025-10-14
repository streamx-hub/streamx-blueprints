package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import com.streamx.blueprints.json.aggregator.stores.DataStore;
import com.streamx.blueprints.json.aggregator.stores.PreservedData;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

public class ProcessMultiValueDataFunction extends AbstractFunction {

  @Inject
  DataStore store;

  @Override
  public DataStore getStore() {
    return store;
  }

  @Override
  protected boolean requiresHashInKey() {
    return true;
  }

  @Incoming(Channels.MULTIVALUED_DATA)
  @Outgoing(Channels.AGGREGATED_MULTIVALUED_DATA)
  Multi<Message<CloudEvent>> processMultiValue(Message<CloudEvent> message) {
    return processDataMessage(message);
  }

  @Override
  protected Optional<Message<CloudEvent>> createMessageForConfig(Configuration config,
      Data data, DataKey key, String eventType, OffsetDateTime eventTime) {
    return Optional.of(mergeMultivaluedResources(eventTime, key.id(), key.namespace(), config));
  }

  private Message<CloudEvent> mergeMultivaluedResources(OffsetDateTime eventTime, String id,
      String namespace, Configuration config) {
    String baseJson = "{\"" + config.outputNamespace() + "\": []}";
    try {
      JsonNode jsonNode = objectMapper.readTree(baseJson);
      ArrayNode result = (ArrayNode) jsonNode.get(config.outputNamespace());
      List<PreservedData> toMerge = store.getAll().stream()
          .filter(res -> DataKey.hasHashAndSameNamespaceAndId(res.getKey(), namespace, id))
          .map(Map.Entry::getValue)
          .toList();
      for (PreservedData entry : toMerge) {
        if (!Data.TYPE_UNPUBLISHED.equals(entry.eventType())) {
          JsonNode item = objectMapper.readTree(entry.data().getContentAsString());
          result.add(item);
        }
      }
      String merged = objectMapper.writeValueAsString(jsonNode);
      log.tracef("Merged json for [%s] %s", id, merged);
      return createPublishMessage(id, eventTime, config.outputNamespace(),
          config.outputType().orElse(null), merged);
    } catch (IOException e) {
      throw new RuntimeException("Unable to deserialize payload", e);
    }
  }
}
