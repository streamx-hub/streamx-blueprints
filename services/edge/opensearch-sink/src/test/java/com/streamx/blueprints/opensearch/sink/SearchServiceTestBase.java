package com.streamx.blueprints.opensearch.sink;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.IndexableResourceFragment;
import io.cloudevents.CloudEvent;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;

abstract class SearchServiceTestBase extends BaseOpensearchTest {

  static final String TEST_KEY = "/test/key";
  static final String TEST_FRAGMENT_KEY = "/fragment/key";
  static final String TEST_TYPE = "test-Type";

  static final String HITS_TOTAL_VALUE = "hits.total.value";
  static final String FIRST_PATH = "hits.hits[0]._id";
  static final String FIRST_TITLE = "hits.hits[0]._source.payload.title";
  static final String FIRST_TYPE = "hits.hits[0]._source.type";
  static final String FIRST_CONTENT_HIGHLIGHT =
      "hits.hits[0].highlight.'payload.content'[0]";
  static final String FIRST_TITLE_HIGHLIGHT = "hits.hits[0].highlight.'payload.title'[0]";
  static final String FIRST_FRAGMENT_HIGHLIGHT =
      "hits.hits[0].highlight.'fragments.payload.content'[0]";

  static final Predicate<ExtractableResponse<Response>> VALIDATE_NO_RESULTS =
      (response) -> response.<Integer>path(HITS_TOTAL_VALUE) == 0;

  static final AtomicInteger eventTimeGenerator = new AtomicInteger(1);

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  ObjectMapper objectMapper;

  private InMemorySource<CloudEvent> indexableResourcesSource;
  private InMemorySource<CloudEvent> indexableResourceFragmentsSource;

  @BeforeEach
  void initSources() {
    indexableResourcesSource = connector.source(Channels.INDEXABLE_RESOURCES);
    indexableResourceFragmentsSource = connector.source(
        Channels.INDEXABLE_RESOURCE_FRAGMENTS);
  }

  void validateNoSearchResultsForTestKey() {
    await().until(() -> getSearchResultByPath(TEST_KEY), VALIDATE_NO_RESULTS);
  }

  void validateNoSearchResultsFor(ExampleIndexableResourceContent content) {
    await().until(() -> getSearchResultByQuery(content.title()), VALIDATE_NO_RESULTS);
    await().until(() -> getSearchResultByQuery(content.content()), VALIDATE_NO_RESULTS);
  }

  void validateNoSearchResultsFor(String content) {
    await().until(() -> getSearchResultByQuery(content), VALIDATE_NO_RESULTS);
  }

  void validateSearchResultsFor(ExampleIndexableResourceContent content) {
    Predicate<ExtractableResponse<Response>> validateIndexedResource = (response) -> {
      Integer size = totalHits(response);
      String path = stringJsonPath(response, FIRST_PATH);
      String title = stringJsonPath(response, FIRST_TITLE);
      String type = stringJsonPath(response, FIRST_TYPE);
      return size > 0
          && TEST_KEY.equalsIgnoreCase(path)
          && TEST_TYPE.equalsIgnoreCase(type)
          && content.title().equalsIgnoreCase(title);
    };

    await().until(() -> getSearchResultByPath(TEST_KEY), validateIndexedResource);
    await().until(() -> getSearchResultByQuery(content.content()),
        (response) -> validateIndexedResource.test(response)
            && stringJsonPath(response, FIRST_CONTENT_HIGHLIGHT).toLowerCase()
            .contains(content.content().toLowerCase())
    );
    await().until(() -> getSearchResultByQuery(content.title()),
        (response) -> validateIndexedResource.test(response)
            && stringJsonPath(response, FIRST_TITLE_HIGHLIGHT).toLowerCase()
            .contains(content.title().toLowerCase())
    );
    validateNotEmptySearchByTypeResults(TEST_TYPE);
  }

  void validateNotEmptySearchByTypeResults(String type) {
    await().until(() -> getSearchResultByType(type),
        (response) -> totalHits(response) > 0
    );
  }

  void validateNoSearchByType(String type) {
    await().until(() -> getSearchResultByType(type),
        (response) -> totalHits(response) == 0
    );
  }

  void validateNoSearchByData(String id, String category, String type) {
    await().until(() -> getSearchResultByData(id, category, type),
        (response) -> totalHits(response) == 0
    );
  }

  void validateNotEmptySearchByData(String id, String category, String type) {
    await().until(() -> getSearchResultByData(id, category, type),
        (response) -> totalHits(response) > 0
    );
  }

  void validateNoFragmentSearchResults(String fragment) {
    await().until(() -> getSearchResultByQuery(fragment),
        (response) -> totalHits(response) == 0);
  }

  void validateFragmentSearchResults(String fragment) {
    await().until(() -> getSearchResultByQuery(fragment),
        (response) -> totalHits(response) > 0
            && stringJsonPath(response, FIRST_PATH).equalsIgnoreCase(TEST_KEY)
            && stringJsonPath(response, FIRST_FRAGMENT_HIGHLIGHT).toLowerCase()
            .contains(fragment.toLowerCase()));
  }

  static Integer totalHits(ExtractableResponse<Response> response) {
    return response.path(HITS_TOTAL_VALUE);
  }

  static String stringJsonPath(ExtractableResponse<Response> response,
      String jsonPath) {
    return response.path(jsonPath);
  }

  protected void publishResource(TestResource content) {
    publishResource(content, TEST_TYPE);
  }

  protected void publishResource(TestResource content, String testType) {
    send(content, IndexableResource.TYPE_PUBLISHED, List.of(), testType);
  }

  protected void publishResource(TestResource content, List<String> fragmentKeys) {
    send(content, IndexableResource.TYPE_PUBLISHED, fragmentKeys, TEST_TYPE);
  }

  void unpublishResource() {
    send(null, IndexableResource.TYPE_UNPUBLISHED, null, null);
  }

  private void send(TestResource content, String eventType, List<String> fragmentKeys,
      String type) {
    try {
      var json = objectMapper.writeValueAsString(content);
      var indexableResource = new IndexableResource(json, type, fragmentKeys);

      CloudEvent event = CloudEventUtils.eventWithData(
          TEST_KEY,
          eventType,
          indexableResource,
          CloudEventUtils.toOffsetDateTime(eventTimeGenerator.getAndIncrement())
      );
      indexableResourcesSource.send(event);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  protected void publishFragment(IndexableResourceFragment fragment) {
    CloudEvent publishEvent = CloudEventUtils.eventWithData(
        TEST_FRAGMENT_KEY,
        IndexableResourceFragment.TYPE_PUBLISHED,
        fragment,
        CloudEventUtils.toOffsetDateTime(eventTimeGenerator.getAndIncrement())
    );
    indexableResourceFragmentsSource.send(publishEvent);
  }

  protected void unpublishFragment() {
    CloudEvent unpublishEvent = CloudEventUtils.eventWithoutData(
        TEST_FRAGMENT_KEY,
        IndexableResourceFragment.TYPE_UNPUBLISHED,
        CloudEventUtils.toOffsetDateTime(eventTimeGenerator.getAndIncrement())
    );
    indexableResourceFragmentsSource.send(unpublishEvent);
  }

  ExtractableResponse<Response> getSearchResultByQuery(String query) {
    return given()
        .basePath("/search/query")
        .param("query", query)
        .when()
        .get()
        .then()
        .statusCode(200)
        .extract();
  }

  ExtractableResponse<Response> getSearchResultByType(String type) {
    return given()
        .basePath("/search/query")
        .param("type", type)
        .when()
        .get()
        .then()
        .statusCode(200)
        .extract();
  }

  ExtractableResponse<Response> getSearchResultByPath(String path) {
    return given()
        .basePath("/search/path")
        .param("path", path)
        .when()
        .get()
        .then()
        .statusCode(200)
        .extract();
  }

  ExtractableResponse<Response> getSearchResultByData(
      String id, String category, String type) {
    return given()
        .basePath("/search/data")
        .param("id", id)
        .param("category", category)
        .param("type", type)
        .when()
        .get()
        .then()
        .statusCode(200)
        .extract();
  }
}
