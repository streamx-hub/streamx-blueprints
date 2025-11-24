package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.data.collector.utils.JsonUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

final class SorterAndLimiter {

  private static final Logger log = Logger.getLogger(SorterAndLimiter.class);

  private SorterAndLimiter() {
    // no instances
  }

  static List<JsonNode> sortAndLimit(List<JsonNode> nodes, String sortBy, SortMode sortMode,
      int max) {
    Stream<JsonNode> results = nodes.stream();
    if (!StringUtils.isBlank(sortBy)) {
      Comparator<JsonNode> comparator = (node1, node2) -> compareNodes(node1, node2, sortBy);
      if (sortMode == SortMode.DESC) {
        comparator = comparator.reversed();
      }
      comparator = Comparator.nullsLast(comparator);
      results = results.sorted(comparator);
    }
    return results.limit(max).toList();
  }

  private static int compareNodes(JsonNode node1, JsonNode node2, String sortBy) {
    Double node1SortByValue = getSortByValue(node1, sortBy);
    Double node2SortByValue = getSortByValue(node2, sortBy);
    if (ObjectUtils.allNotNull(node1SortByValue, node2SortByValue)) {
      return Double.compare(node1SortByValue, node2SortByValue);
    }
    if (ObjectUtils.allNull(node1SortByValue, node2SortByValue)) {
      return 0;
    }
    if (node1SortByValue != null) {
      return -1;
    }
    return 1;
  }

  private static Double getSortByValue(JsonNode dataJsonNode, String sortBy) {
    Set<String> sortByValue = JsonUtils.getValues(dataJsonNode, sortBy);
    if (!sortByValue.isEmpty()) {
      try {
        return Double.parseDouble(sortByValue.iterator().next());
      } catch (NumberFormatException e) {
        log.tracef("Cannot parse as double: %s", sortByValue);
      }
    }
    return null;
  }
}
