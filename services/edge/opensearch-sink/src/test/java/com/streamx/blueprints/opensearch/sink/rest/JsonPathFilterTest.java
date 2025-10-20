package com.streamx.blueprints.opensearch.sink.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.opensearch.NoOpensearchProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoOpensearchProfile.class)
class JsonPathFilterTest {

  @Inject
  ObjectMapper objectMapper;

  @Inject
  JsonPathFilter uut;

  @Test
  void shouldReturnEmptyJsonNodeForNoJsonPathSpecified() {
    // given
    var source = prepareJsonNode("""
        {"property": "value" }""");
    var jsonPaths = List.of("");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{}");
  }

  @Test
  void shouldReturnEmptyJsonNodeForNoJsonPathFound() {
    // given
    var source = prepareJsonNode("""
        {"property": "value" }""");
    var jsonPaths = List.of("nonexisting.jsonPath");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{}");
  }

  @Test
  void shouldReturnEmptyJsonNodeForOnlyRootJsonPathSpecified() {
    // given
    var source = prepareJsonNode("""
        {"property": "value" }""");
    var jsonPaths = List.of("$");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{}");
  }

  @Test
  void shouldReturnEmptyJsonNodeForExactJsonPathSpecified() {
    // given
    String json = """
        {"property":"value"}""";
    var source = prepareJsonNode(json);
    var jsonPaths = List.of("$.property");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo(json);
  }

  @Test
  void shouldFilterOutPropertyWithSameStringValueNotFromSpecifiedJsonPath() {
    // given
    var source = prepareJsonNode("""
        {"property":"value"},{"unwantedProperty":"value"}""");
    var jsonPaths = List.of("$.property");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{\"property\":\"value\"}");
  }

  @Test
  void shouldFilterOutPropertyWithInitiallySharedIntNodeFromNonSpecifiedJsonPath() {
    // given
    var source = prepareJsonNode("""
        {"property":1,"nested":{"unwantedProperty":1}}""");
    var jsonPaths = List.of("$.property");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{\"property\":1}");
  }

  @Test
  void shouldFilterArraysPropertyNotFromSpecifiedJsonPath() {
    // given
    var source = prepareJsonNode("""
        {"array":[{"property":"value"},{"unwantedProperty":"value"}]}""");
    var jsonPaths = List.of("array.*.property");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{\"array\":[{\"property\":\"value\"}]}");
  }

  @Test
  void shouldFilterObjectsPropertyNotFromSpecifiedJsonPath() {
    // given
    var source = prepareJsonNode("""
        {"object":{"p1":{"property":"value"},"p2":{"unwantedProperty":"value"}}}""");
    var jsonPaths = List.of("object.p1.property");

    // when
    var result = stringifyResult(source, jsonPaths);

    // then
    assertThat(result).isEqualTo("{\"object\":{\"p1\":{\"property\":\"value\"}}}");
  }

  private String stringifyResult(JsonNode source, List<String> jsonPaths) {
    try {
      return objectMapper.writeValueAsString(uut.filterJson(source, jsonPaths));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonNode prepareJsonNode(String content) {
    try {
      return objectMapper.readTree(content);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}