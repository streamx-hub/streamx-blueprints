package com.streamx.blueprints.json.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.AggregatorConfiguration;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

abstract class AbstractFunction {

  protected static final int NAMESPACE_POSITION = 0;
  protected static final int ID_POSITION = 1;
  protected static final String KEY_SEPARATOR = ":";

  protected static final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  Logger log;

  @Inject
  AggregatorConfiguration aggregatorConfig;

  protected final Map<Configuration, Set<String>> supportedNamespacesByConfig
      = new LinkedHashMap<>();

  protected abstract int expectedKeyPartsCount();

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

  protected Message<CloudEvent> createPublishMessage(String id, OffsetDateTime eventTime,
      String outputNamespace, String outputType, String payload) {
    String key = outputNamespace + KEY_SEPARATOR + id;
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
    String key = outputNamespace + KEY_SEPARATOR + id;
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
    String[] keyParts = key.split(KEY_SEPARATOR);
    if (keyParts.length != expectedKeyPartsCount()) {
      return false;
    }
    String id = keyParts[ID_POSITION];
    String namespace = keyParts[NAMESPACE_POSITION];
    return StringUtils.isNotEmpty(id)
        && supportedNamespacesByConfig.values().stream()
        .anyMatch(supportedNamespaces -> supportedNamespaces.contains(namespace));
  }

  protected List<Configuration> getConfigurations(String namespace) {
    return supportedNamespacesByConfig.entrySet().stream()
        .filter(entry -> entry.getValue().contains(namespace))
        .map(Entry::getKey).toList();
  }
}
