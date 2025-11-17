package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.AggregatorConfiguration;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import com.streamx.blueprints.json.aggregator.stores.DataStore;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

abstract class AbstractFunction {

  protected static final ObjectMapper objectMapper = new ObjectMapper();

  Logger log;

  @Inject
  DataStore store;

  @Inject
  AggregatorConfiguration aggregatorConfig;

  private final Map<Configuration, Set<String>> supportedNamespacesByConfig = new LinkedHashMap<>();

  protected abstract boolean requiresHashInKey();

  protected abstract Optional<CloudEvent> createEventForConfig(Configuration config,
      CloudEvent inputEvent, Data data, DataKey dataKey);

  @PostConstruct
  void init() {
    log = Logger.getLogger(getClass());
    for (Configuration config : aggregatorConfig.configurations()) {
      Set<String> supportedNamespaces = new LinkedHashSet<>();
      supportedNamespaces.add(config.masterNamespace());
      supportedNamespaces.addAll(config.optionalNamespaces().orElseGet(List::of));
      if (supportedNamespaces.contains(config.outputNamespace())) {
        throw new IllegalStateException("Output must be different from any input namespace");
      }
      supportedNamespacesByConfig.put(config, supportedNamespaces);
    }
  }

  protected Multi<Message<CloudEvent>> processDataMessage(Message<CloudEvent> message) {
    CloudEvent event = message.getPayload();
    Data data = CloudEventUtils.getData(event, Data.class);
    String eventType = event.getType();
    String key = CloudEventUtils.getSubject(event);
    OffsetDateTime eventTime = event.getTime();

    store.register(data, eventType, key);
    log.tracef("Processing message [%s] with event time %s", key, eventTime);

    try {
      if (!accept(key)) {
        log.tracef("Skipping invalid incoming message key=%s", key);
        message.ack();
        return Multi.createFrom().empty();
      }

      List<CloudEvent> resultEvents = new LinkedList<>();
      DataKey dataKey = DataKey.fromKey(key);
      List<Configuration> matchingConfigurations = getConfigurations(dataKey.namespace());
      for (Configuration config : matchingConfigurations) {
        createEventForConfig(config, event, data, dataKey).ifPresent(resultEvents::add);
      }
      return Multi.createFrom().items(resultEvents.stream().map(Message::of))
          .onCompletion()
          .call(() -> Uni.createFrom().completionStage(message.ack()));
    } catch (Exception e) {
      log.warnf(e, "Error processing data message %s", key);
      message.nack(e);
      return Multi.createFrom().empty();
    }
  }

  protected CloudEvent createPublishEvent(CloudEvent inputEvent, String id, String outputNamespace,
      String outputType, String payload) {
    String key = DataKey.fromNamespaceAndId(outputNamespace, id);
    log.tracef("Creating Data Publish message with key %s and outputType %s", key, outputType);
    return CloudEventUtils.eventCopyWithData(inputEvent, new Data(payload, outputType))
        .withSubject(key)
        .withType(Data.TYPE_PUBLISHED)
        .build();
  }

  protected CloudEvent createUnpublishEvent(CloudEvent inputEvent, String id,
      String outputNamespace) {
    String key = DataKey.fromNamespaceAndId(outputNamespace, id);
    log.tracef("Creating Data Unpublish message with key %s", key);
    return CloudEventUtils.eventCopyWithoutData(inputEvent)
        .withSubject(key)
        .withType(Data.TYPE_UNPUBLISHED)
        .build();
  }

  private boolean accept(String key) {
    DataKey dataKey = DataKey.fromKey(key);

    if (!dataKey.hasNamespaceAndId()) {
      log.tracef("Expected namespace and ID in key %s", key);
      return false;
    }

    if (requiresHashInKey() && !dataKey.hasHash()) {
      log.tracef("Expected hash in key %s, but it's missing", key);
      return false;
    }

    boolean anyMatchingNamespace = supportedNamespacesByConfig.values().stream()
        .anyMatch(supportedNamespaces -> supportedNamespaces.contains(dataKey.namespace()));
    if (!anyMatchingNamespace) {
      log.tracef("No matching namespace for %s", key);
      return false;
    }

    return true;
  }

  private List<Configuration> getConfigurations(String namespace) {
    return supportedNamespacesByConfig.entrySet().stream()
        .filter(entry -> entry.getValue().contains(namespace))
        .map(Entry::getKey).toList();
  }

  protected Set<String> getNamespacesByConfig(Configuration config) {
    return supportedNamespacesByConfig.get(config);
  }
}
