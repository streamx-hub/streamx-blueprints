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
import com.streamx.blueprints.data.collector.collectors.Collector;
import com.streamx.blueprints.data.collector.collectors.DataFilter;
import com.streamx.blueprints.data.collector.utils.JsonUtils;
import dev.streamx.blueprints.data.Data;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import dev.streamx.quasar.reactive.messaging.utils.MetadataUtils;
import io.smallrye.reactive.messaging.GenericPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

public class AggregateByPropertyValueCollector implements Collector {

  private final Store<Data> dataStore;
  private final String outputKeyPrefix;
  private final String[] filterBy;
  private final String groupBy;
  private final String sortBy;
  private final SortMode sortMode;
  private final int max;
  Logger log = Logger.getLogger(AggregateByPropertyValueCollector.class);
  DataFilter dataFilter;

  AggregateByPropertyValueCollector(Store<Data> dataStore, DataFilter dataFilter,
      String outputKeyPrefix, String[] filterBy, String groupBy,
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
  public boolean process(Key key, Data data, Action action) {
    // Always recalculate collected data after data update.
    return true;
  }

  @Override
  public List<CollectedOutput> collect() {
    long startTime = System.currentTimeMillis();

    Map<String, List<JsonNode>> groupedData = new HashMap<>();
    dataStore.entriesWithMetadata()
        .filter(entry -> Action.PUBLISH.equals(
            entry.value().getMetadata().get(Action.class).orElse(null)))
        .filter(entry -> entry.value().getPayload() != null
            && entry.value().getPayload().getContent() != null)
        .filter(entry -> isMatchingDataPatterns(entry.value()))
        .map(entry -> entry.value().getPayload().getContentAsString())
        .map(JsonUtils::parseToJsonNode)
        .filter(this::filterBy)
        .forEach(entry -> generateGroupedData(entry, groupedData));
    List<CollectedOutput> results = groupedData.entrySet().stream()
        .map(entry -> {
          String key = outputKeyPrefix + entry.getKey()
              .replaceAll(" ", "_")
              .replaceAll("[^A-Za-z0-9_]", "");
          List<JsonNode> sortedAndLimited = sortAndLimit(entry.getValue());
          ObjectNode collectedDataObject = createCollectedDataObject(key, sortedAndLimited);
          return new CollectedOutput(Key.of(key), new Data(collectedDataObject.toString()));
        }).toList();
    log.debugf("Collected %s results in %s ms", results.size(),
        System.currentTimeMillis() - startTime);
    return results;
  }

  boolean isMatchingDataPatterns(GenericPayload<Data> data) {
    String dataKey = MetadataUtils.extractKey(data);
    String dataType = Properties.from(data).getType().orElse(null);
    return dataFilter.test(dataKey, dataType);
  }

  private void generateGroupedData(JsonNode entry, Map<String, List<JsonNode>> groupedData) {
    String[] groupByEntries = JsonUtils.getValues(entry, groupBy);
    for (String groupByEntry : groupByEntries) {
      if (containsValue(entry, groupBy, groupByEntry)) {
        if (!groupedData.containsKey(groupByEntry)) {
          groupedData.put(groupByEntry, new ArrayList<>());
        }
        groupedData.get(groupByEntry).add(entry);
      }
    }
  }

  private boolean filterBy(JsonNode jsonEntry) {
    if (ArrayUtils.isEmpty(filterBy)) {
      return true;
    }
    boolean isValid = true;
    for (String filter : filterBy) {
      if (!isValid) {
        break;
      }
      var parse = JsonPath.read(jsonEntry, filter);
      if (parse instanceof ArrayNode arrayNode) {
        isValid = !arrayNode.isEmpty();
      } else if (parse instanceof Collection<?> collection) {
        isValid = !collection.isEmpty();
      } else {
        isValid = Objects.nonNull(parse);
      }
    }
    return isValid;
  }

  private Double getSortBy(JsonNode dataJsonNode) {
    String[] sortByValue = JsonUtils.getValues(dataJsonNode, sortBy);
    if (ArrayUtils.isNotEmpty(sortByValue)) {
      try {
        return Double.parseDouble(sortByValue[0]);
      } catch (NumberFormatException e) {
        log.tracef("Cannot parse as double: %s", sortByValue);
      }
    }
    return null;
  }

  private ObjectNode createCollectedDataObject(String key, List<JsonNode> values) {
    ObjectNode collectedDataObject = OBJECT_MAPPER.createObjectNode();
    ArrayNode valuesJsonArray = OBJECT_MAPPER.createArrayNode();
    values.forEach(valuesJsonArray::add);
    collectedDataObject.put("key", key);
    collectedDataObject.set("values", valuesJsonArray);
    return collectedDataObject;
  }

  private List<JsonNode> sortAndLimit(List<JsonNode> list) {
    Stream<JsonNode> results = list.stream();
    if (!StringUtils.isBlank(sortBy)) {
      Comparator<JsonNode> comparator = (node1, node2) -> {
        Double node1SortBy = getSortBy(node1);
        Double node2SortBy = getSortBy(node2);
        if (node1SortBy != null && node2SortBy != null) {
          return Double.compare(node1SortBy, node2SortBy);
        } else if (node1SortBy == null && node2SortBy == null) {
          return 0;
        } else if (node1SortBy != null) {
          return -1;
        } else {
          return 1;
        }
      };
      if (sortMode == SortMode.DESC) {
        comparator = comparator.reversed();
      }
      comparator = Comparator.nullsLast(comparator);
      results = results.sorted(comparator);
    }
    return results.limit(max).toList();
  }

  enum SortMode {
    ASC, DESC;
  }

}
