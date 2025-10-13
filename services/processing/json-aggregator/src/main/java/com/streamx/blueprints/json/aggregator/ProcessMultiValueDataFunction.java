package com.streamx.blueprints.json.aggregator;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

public class ProcessMultiValueDataFunction extends AbstractFunction {

  @Inject
  Logger log;

  @Inject
  @FromChannel(CHANNEL_MULTIVALUED_DATA)
  Store<Data> store;

  @Incoming(CHANNEL_MULTIVALUED_DATA)
  @Outgoing(CHANNEL_AGGREGATED_MULTIVALUED_DATA)
  Multi<Message<Data>> processMultiValue(Message<Data> message) {
    List<Message<Data>> result = new LinkedList<>();
    String key = extractKey(message);
    log.tracef("Processing message [%s]", key);
    Action action = extractAction(message);
    Long eventTime = extractEventTime(message);
    try {
      String[] keyParts = key.split(KEY_SEPARATOR);
      if (!accept(key, action, eventTime)) {
        log.tracef("Skipping invalid incoming message key=%s", key);
      } else {
        String id = keyParts[ID_POSITION];
        String namespace = keyParts[NAMESPACE_POSITION];
        for (Configuration config : getConfigurations(namespace)) {
          result.add(mergeMultivaluedResources(eventTime, id, namespace, config));
        }
      }
      return Multi.createFrom().iterable(result)
          .onCompletion()
          .call(() -> Uni.createFrom().completionStage(message.ack()));
    } catch (Exception e) {
      message.nack(e);
      return Multi.createFrom().empty();
    }
  }

  private boolean accept(String key, Action action, Long eventTime) {
    return super.accept(key) && action != null && eventTime != null
        && key.split(KEY_SEPARATOR).length == 3;
  }

  private Message<Data> mergeMultivaluedResources(Long eventTime, String id,
      String namespace, Configuration config) {
    ObjectMapper objectMapper = new ObjectMapper();
    String baseJson = "{\"" + config.outputNamespace() + "\": []}";
    try {
      JsonNode jsonNode = objectMapper.readTree(baseJson);
      ArrayNode result = (ArrayNode) jsonNode.get(config.outputNamespace());
      List<GenericPayload<Data>> toMerge = store.entriesWithMetadata()
          .filter(res -> res.key().startsWith(namespace + KEY_SEPARATOR + id + KEY_SEPARATOR))
          .map(Store.Entry::value)
          .toList();
      for (GenericPayload<Data> entry : toMerge) {
        if (!Action.UNPUBLISH.equals(Action.from(entry.getMetadata()))) {
          JsonNode item = objectMapper.readTree(entry.getPayload().getContentAsString());
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
