package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SorterAndLimiterTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldSortAndLimit() throws Exception {
    // given
    List<JsonNode> jsonNodes = List.of(toJsonNode("""
            {
              "name": "a",
              "value": "2"
            }
            """),
        toJsonNode("""
            {
              "name": "b",
              "value": "1"
            }
            """),
        toJsonNode("""
            {
              "name": "c",
              "value": "3"
            }
            """)
    );

    String sortBy = "value";
    int limit = 2;

    // when
    List<JsonNode> sortedNodes = SorterAndLimiter.sortAndLimit(
        jsonNodes, sortBy, SortMode.DESC, limit);

    // then
    assertThat(sortedNodes).hasSize(limit);
    assertJsonNode(sortedNodes.get(0), """
        {
          "name" : "c",
          "value" : "3"
        }"""
    );
    assertJsonNode(sortedNodes.get(1), """
        {
          "name" : "a",
          "value" : "2"
        }"""
    );
  }

  private static void assertJsonNode(JsonNode jsonNode, String expectedContentJson)
      throws JsonProcessingException {
    assertThat(formatJson(jsonNode))
        .isEqualTo(formatJson(expectedContentJson));
  }

  private static JsonNode toJsonNode(String json) throws JsonProcessingException {
    return objectMapper.readTree(json);
  }

  private static String formatJson(String json) throws JsonProcessingException {
    JsonNode jsonNode = objectMapper.readTree(json);
    return formatJson(jsonNode);
  }

  private static String formatJson(JsonNode jsonNode) throws JsonProcessingException {
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
  }
}