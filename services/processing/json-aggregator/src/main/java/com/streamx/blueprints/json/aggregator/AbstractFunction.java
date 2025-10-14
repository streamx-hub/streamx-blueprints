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

  @Inject
  Logger log;

  @Inject
  AggregatorConfiguration aggregatorConfig;

  protected final Map<Configuration, Set<String>> supportedNamespacesByConfig
      = new LinkedHashMap<>();

  protected abstract DataStore getStore();

  protected abstract boolean requiresHashInKey();

  protected abstract Optional<Message<CloudEvent>> createMessageForConfig(Configuration config,
      Data data, DataKey dataKey, String eventType, OffsetDateTime eventTime);

  @PostConstruct
  void init() {
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

    getStore().register(data, eventType, key);
    log.tracef("Processing message [%s]", key);

    try {
      if (!accept(key, eventTime)) {
        log.tracef("Skipping invalid incoming message key=%s", key);
        message.ack();
        return Multi.createFrom().empty();
      }

      List<Message<CloudEvent>> resultMessages = new LinkedList<>();
      DataKey dataKey = DataKey.fromKey(key);
      List<Configuration> matchingConfigurations = getConfigurations(dataKey.namespace());
      for (Configuration config : matchingConfigurations) {
        createMessageForConfig(config, data, dataKey, eventType, eventTime)
            .ifPresent(resultMessages::add);
      }
      return Multi.createFrom().iterable(resultMessages)
          .onCompletion()
          .call(() -> Uni.createFrom().completionStage(message.ack()));
    } catch (Exception e) {
      message.nack(e);
      return Multi.createFrom().empty();
    }
  }

  protected Message<CloudEvent> createPublishMessage(String id, OffsetDateTime eventTime,
      String outputNamespace, String outputType, String payload) {
    String key = DataKey.fromNamespaceAndId(outputNamespace, id);
    log.tracef("Creating Data Publish message with key %s and outputType %s", key, outputType);
    return Message.of(
        CloudEventUtils.eventWithData(
            key,
            Data.TYPE_PUBLISHED,
            new Data(payload, outputType),
            eventTime
        )
    );
  }

  protected Message<CloudEvent> createUnpublishMessage(String id, OffsetDateTime eventTime,
      String outputNamespace) {
    String key = DataKey.fromNamespaceAndId(outputNamespace, id);
    log.tracef("Creating Data Unpublish message with key %s", key);
    return Message.of(
        CloudEventUtils.eventWithoutData(
            key,
            Data.TYPE_UNPUBLISHED,
            eventTime
        )
    );
  }

  protected boolean accept(String key, OffsetDateTime eventTime) {
    if (eventTime == null) {
      return false;
    }
    DataKey dataKey = DataKey.fromKey(key);
    if (!dataKey.hasNamespaceAndId()) {
      return false;
    }
    if (requiresHashInKey() && !dataKey.hasHash()) {
      return false;
    }
    return supportedNamespacesByConfig.values().stream()
        .anyMatch(supportedNamespaces -> supportedNamespaces.contains(dataKey.namespace()));
  }

  protected List<Configuration> getConfigurations(String namespace) {
    return supportedNamespacesByConfig.entrySet().stream()
        .filter(entry -> entry.getValue().contains(namespace))
        .map(Entry::getKey).toList();
  }
}
