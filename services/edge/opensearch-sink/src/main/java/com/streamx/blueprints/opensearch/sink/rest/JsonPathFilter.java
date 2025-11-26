package com.streamx.blueprints.opensearch.sink.rest;

import static com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST;
import static com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
class JsonPathFilter {

  JsonPathFilter() {
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

  /**
   * This method traverses the source JSON object and removes redundant JSON nodes.
   *
   * @param source JSON that will be filtered
   * @param allowedJsonPaths JSON paths that must not be filtered out.
   * @return JSON with only allowed JSON paths.
   */
  JsonNode filterJson(JsonNode source, List<String> allowedJsonPaths) {
    var documentContext = JsonPath
        .using(Configuration.defaultConfiguration())
        .parse(source);

    IntNodeReplacer.replaceIntNode(documentContext.json());
    var remainingJsonNodes = calculateRemainingJsonNodes(allowedJsonPaths, documentContext);
    filterNode(documentContext.json(), remainingJsonNodes);

    return documentContext.json();
  }

  private static Set<JsonNodeWrapper> calculateRemainingJsonNodes(List<String> allowedJsonPaths,
      DocumentContext documentContext) {
    var remainingJsonNodes = new HashSet<JsonNodeWrapper>();

    for (var allowedJsonPath : allowedJsonPaths) {
      if (StringUtils.isNotBlank(allowedJsonPath)) {
        ArrayNode jsonPathResultNodes = documentContext.read(allowedJsonPath);
        jsonPathResultNodes.forEach(node -> remainingJsonNodes.add(new JsonNodeWrapper(node)));
      }
    }
    return remainingJsonNodes;
  }

  private boolean filterNode(JsonNode node, Set<JsonNodeWrapper> nodesFromJsonPaths) {
    var foundInJsonPaths = nodesFromJsonPaths.contains(new JsonNodeWrapper(node));
    var anyChildFoundInJsonPaths = false;

    if (node.isObject()) {
      anyChildFoundInJsonPaths = filterObjectNode((ObjectNode) node, nodesFromJsonPaths);
    } else if (node.isArray()) {
      anyChildFoundInJsonPaths = filterArrayNode((ArrayNode) node, nodesFromJsonPaths);
    }

    return foundInJsonPaths || anyChildFoundInJsonPaths;
  }

  private boolean filterArrayNode(ArrayNode node,
      Set<JsonNodeWrapper> nodesFromJsonPaths) {
    int matchesCount = 0;
    for (int i = node.size() - 1; i >= 0; i--) {
      if (filterArrayElement(node, i, nodesFromJsonPaths)) {
        matchesCount++;
      }
    }
    return matchesCount > 0;
  }

  private boolean filterArrayElement(
      ArrayNode arrayNode, int i, Set<JsonNodeWrapper> nodesFromJsonPaths) {
    var child = arrayNode.get(i);
    var remainChild = filterNode(child, nodesFromJsonPaths);

    if (!remainChild) {
      arrayNode.remove(i);
    }
    return remainChild;
  }

  private boolean filterObjectNode(ObjectNode node, Set<JsonNodeWrapper> nodesFromJsonPaths) {
    boolean anyChildFoundInJsonPaths = false;

    for (String fieldName : asList(node.fieldNames())) {
      anyChildFoundInJsonPaths =
           filterObjectProperty(node, fieldName, nodesFromJsonPaths)
               || anyChildFoundInJsonPaths;
    }
    return anyChildFoundInJsonPaths;
  }

  private boolean filterObjectProperty(ObjectNode objectNode,
      String fieldName, Set<JsonNodeWrapper> nodesFromJsonPaths) {
    var child = objectNode.get(fieldName);
    var remainChild = filterNode(child, nodesFromJsonPaths);

    if (!remainChild) {
      objectNode.remove(fieldName);
    }

    return remainChild;
  }

  /**
   * This class is required to avoid deduplication of JsonNodes in Sets.
   * By default, JsonNode is equal to other if value containing is same.
   * However, equal JsonNodes can be placed in different JSON paths,
   * so we need "hashCode" and "equal" methods basing on object identity.
   */
  private record JsonNodeWrapper(JsonNode jsonNode) {

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      JsonNodeWrapper that = (JsonNodeWrapper) o;
      // comparing object identity is intentional to avoid deduplication of equal JsonNodes
      return jsonNode == that.jsonNode;
    }
  }

  private static class IntNodeReplacer {

    /**
     * Jackson caches IntNodes in range -1 to 10 {@link IntNode#valueOf(int)}.
     * However, JsonPathFilter requires unique, non-shared object for each JSON node.
     * <br/>
     * This method traverses JSON node and replaces shared IntNodes with non-shared nodes.
     * @param node JSON tree to traverse and replace IntNodes with non-shared nodes.
     */
    private static void replaceIntNode(JsonNode node) {
      if (node.isObject()) {
        replaceIntNodeInObjectNode((ObjectNode) node);
      } else if (node.isArray()) {
        replaceIntNodeInArrayNode((ArrayNode) node);
      }
    }

    private static void replaceIntNodeInArrayNode(ArrayNode node) {
      for (int i = node.size() - 1; i >= 0; i--) {
        replaceIntNodeInArrayElement(node, i);
      }
    }

    private static void replaceIntNodeInArrayElement(
        ArrayNode arrayNode, int i) {
      var child = arrayNode.get(i);

      if (child instanceof IntNode) {
        arrayNode.set(i, new IntNode(child.intValue()));
      } else {
        replaceIntNode(child);
      }
    }

    private static void replaceIntNodeInObjectNode(ObjectNode node) {
      for (String fieldName : asList(node.fieldNames())) {
        replaceIntNodeInObjectProperty(node, fieldName);
      }
    }

    private static void replaceIntNodeInObjectProperty(ObjectNode objectNode, String fieldName) {
      var child = objectNode.get(fieldName);
      if (child instanceof IntNode) {
        objectNode.set(fieldName, new IntNode(child.intValue()));
      } else {
        replaceIntNode(child);
      }
    }
  }

  private static List<String> asList(Iterator<String> iterator) {
    List<String> result = new LinkedList<>();
    while (iterator.hasNext()) {
      result.add(iterator.next());
    }
    return result;
  }
}
