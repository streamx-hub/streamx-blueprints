package com.streamx.blueprints.opensearch.sink.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DefaultSearchResultTransformerTest {

  @Inject
  DefaultSearchResultTransformer uut;

  @Inject
  ObjectMapper objectMapper;

  @Test
  void shouldFilterExampleOpensearchResponse() throws IOException {
    var responseUrl = getClass().getResource("example-opensearch-response.json");
    var opensearchResponse = objectMapper.readTree(responseUrl);

    var filteredUrl = getClass().getResource("filtered-example-opensearch-response.json");
    var filteredOpensearchResponse = objectMapper.readTree(filteredUrl);

    var result = uut.transform(opensearchResponse);

    assertThat(result).isEqualTo(filteredOpensearchResponse);
  }
}