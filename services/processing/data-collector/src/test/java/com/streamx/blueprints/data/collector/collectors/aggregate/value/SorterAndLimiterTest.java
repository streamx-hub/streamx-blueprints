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
    assertThat(sortedNodes)
        .hasSize(limit)
        .extracting(JsonNode::toPrettyString)
        .containsExactly(
            """
                {
                  "name" : "c",
                  "value" : "3"
                }""",
            """
                {
                  "name" : "a",
                  "value" : "2"
                }"""
        );
  }

  private static JsonNode toJsonNode(String json) throws JsonProcessingException {
    return objectMapper.readTree(json);
  }

}