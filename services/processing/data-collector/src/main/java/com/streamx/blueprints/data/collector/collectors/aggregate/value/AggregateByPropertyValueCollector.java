package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import static com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST;
import static com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS;
import static com.streamx.blueprints.data.collector.utils.JsonUtils.OBJECT_MAPPER;
import static com.streamx.blueprints.data.collector.utils.JsonUtils.containsValue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.stores.PublishedDataStore;
import com.streamx.blueprints.data.collector.utils.JsonUtils;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jboss.logging.Logger;

public class AggregateByPropertyValueCollector implements Collector {

  private static final Logger log = Logger.getLogger(AggregateByPropertyValueCollector.class);

  private final PublishedDataStore dataStore;
  private final DataFilter dataFilter;
  private final String outputKeyPrefix;
  private final List<String> filterBy;
  private final String groupBy;
  private final String sortBy;
  private final SortMode sortMode;
  private final int max;

  AggregateByPropertyValueCollector(PublishedDataStore dataStore, DataFilter dataFilter,
      String outputKeyPrefix, List<String> filterBy, String groupBy,
      String sortBy, SortMode sortMode,
      int max) {
    this.dataStore = dataStore;
    this.dataFilter = dataFilter;
    this.outputKeyPrefix = outputKeyPrefix;
    this.filterBy = filterBy;
    this.groupBy = groupBy;
    this.sortBy = sortBy;
    this.sortMode = sortMode;
    this.max = max;

    var objectMapper = new ObjectMapper();
    var jsonProvider = new JacksonJsonNodeJsonProvider(objectMapper);
    var mappingProvider = new JacksonMappingProvider(objectMapper);

    Configuration.setDefaults(new Configuration.Defaults() {
      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public Set<Option> options() {
        return Set.of(ALWAYS_RETURN_LIST, SUPPRESS_EXCEPTIONS);
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }
    });
  }

  @Override
  public boolean process(String key, Data data, String eventType) {
    // Always recalculate collected data after data update.
    return true;
  }

  @Override
  public List<CollectedOutput> collect() {
    long startTime = System.currentTimeMillis();

    Map<String, List<JsonNode>> groupedData = new LinkedHashMap<>();
    dataStore.getAll().stream()
        .filter(value -> !Resource.isEmpty(value.data()))
        .filter(value -> isMatchingDataPatterns(value.key(), value.data()))
        .map(value -> value.data().getContentAsString())
        .map(JsonUtils::parseToJsonNode)
        .filter(this::isValidForFiltering)
        .forEach(entry -> generateGroupedData(entry, groupedData));
    List<CollectedOutput> results = groupedData.entrySet().stream()
        .map(entry -> {
          String key = outputKeyPrefix + entry.getKey()
              .replaceAll(" ", "_")
              .replaceAll("[^A-Za-z0-9_]", "");
          List<JsonNode> sortedAndLimited = SorterAndLimiter.sortAndLimit(entry.getValue(),
              sortBy, sortMode, max);
          ObjectNode collectedDataObject = createCollectedDataObject(key, sortedAndLimited);
          return new CollectedOutput(key, collectedDataObject.toString());
        }).toList();
    log.debugf("Collected %s results in %s ms", results.size(),
        System.currentTimeMillis() - startTime);
    return results;
  }

  boolean isMatchingDataPatterns(String key, Data data) {
    return dataFilter.test(key, data.getType());
  }

  private void generateGroupedData(JsonNode entry, Map<String, List<JsonNode>> groupedData) {
    Set<String> groupByEntries = JsonUtils.getValues(entry, groupBy);
    for (String groupByEntry : groupByEntries) {
      if (containsValue(entry, groupBy, groupByEntry)) {
        groupedData.computeIfAbsent(groupByEntry, k -> new LinkedList<>()).add(entry);
      }
    }
  }

  private boolean isValidForFiltering(JsonNode jsonEntry) {
    for (String filter : filterBy) {
      Object parsedValue = JsonPath.read(jsonEntry, filter);
      if (!isValid(parsedValue)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValid(Object parsedValue) {
    if (parsedValue instanceof ArrayNode arrayNode) {
      return !arrayNode.isEmpty();
    }
    if (parsedValue instanceof Collection<?> collection) {
      return !collection.isEmpty();
    }
    return Objects.nonNull(parsedValue);
  }

  private ObjectNode createCollectedDataObject(String key, List<JsonNode> values) {
    ObjectNode collectedDataObject = OBJECT_MAPPER.createObjectNode();
    ArrayNode valuesJsonArray = OBJECT_MAPPER.createArrayNode();
    values.forEach(valuesJsonArray::add);
    collectedDataObject.put("key", key);
    collectedDataObject.set("values", valuesJsonArray);
    return collectedDataObject;
  }

}
