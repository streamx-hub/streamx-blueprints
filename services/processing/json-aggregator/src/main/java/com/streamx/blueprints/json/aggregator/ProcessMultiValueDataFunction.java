package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import com.streamx.blueprints.json.aggregator.stores.DataStore;
import com.streamx.blueprints.json.aggregator.stores.PreservedData;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

public class ProcessMultiValueDataFunction extends AbstractFunction {

  @Inject
  DataStore store;

  @Override
  protected int expectedKeyPartsCount() {
    return 3;
  }

  @Incoming(Channels.MULTIVALUED_DATA)
  @Outgoing(Channels.AGGREGATED_MULTIVALUED_DATA)
  Multi<Message<CloudEvent>> processMultiValue(Message<CloudEvent> message) {
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
        resultMessages.add(mergeMultivaluedResources(eventTime, id, namespace, config));
      }
      return Multi.createFrom().iterable(resultMessages)
          .onCompletion()
          .call(() -> Uni.createFrom().completionStage(message.ack()));
    } catch (Exception e) {
      message.nack(e);
      return Multi.createFrom().empty();
    }
  }

  private Message<CloudEvent> mergeMultivaluedResources(OffsetDateTime eventTime, String id,
      String namespace, Configuration config) {
    String baseJson = "{\"" + config.outputNamespace() + "\": []}";
    try {
      JsonNode jsonNode = objectMapper.readTree(baseJson);
      ArrayNode result = (ArrayNode) jsonNode.get(config.outputNamespace());
      List<PreservedData> toMerge = store.getAll().stream()
          .filter(res -> res.getKey().startsWith(namespace + KEY_SEPARATOR + id + KEY_SEPARATOR))
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
