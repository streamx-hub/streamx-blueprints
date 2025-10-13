package com.streamx.blueprints.json.aggregator;

import dev.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.configuration.AggregatorConfiguration;
import com.streamx.blueprints.json.aggregator.configuration.Configuration;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

abstract class AbstractFunction {

  public static final String CHANNEL_DATA = "data";

  public static final String CHANNEL_MULTIVALUED_DATA = "multivalued-data";
  public static final String CHANNEL_AGGREGATED_DATA = "aggregated-data";

  public static final String CHANNEL_AGGREGATED_MULTIVALUED_DATA = "aggregated-multivalued-data";

  protected static final int NAMESPACE_POSITION = 0;
  protected static final int ID_POSITION = 1;
  protected static final String KEY_SEPARATOR = ":";

  @Inject
  protected AggregatorConfiguration aggregatorConfig;

  protected final Map<Configuration, Set<String>> supportedNamespacesByConfig
      = new LinkedHashMap<>();

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

  protected static Message<Data> createPublishMessage(String id, long eventTime,
      String outputNamespace, String outputType, String payload) {
    return Message.of(
        new Data(payload),
        Metadata.of(
            Key.of(outputNamespace + KEY_SEPARATOR + id),
            Action.PUBLISH,
            EventTime.of(eventTime),
            Properties.empty().withType(outputType)));
  }

  protected static Message<Data> createUnpublishMessage(String id, long eventTime,
      String outputNamespace) {
    return Message.of(
        null,
        Metadata.of(
            Key.of(outputNamespace + KEY_SEPARATOR + id),
            Action.UNPUBLISH,
            EventTime.of(eventTime)));
  }

  protected boolean accept(String key) {
    String[] keyParts = key.split(KEY_SEPARATOR);
    if (keyParts.length < 2) {
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
