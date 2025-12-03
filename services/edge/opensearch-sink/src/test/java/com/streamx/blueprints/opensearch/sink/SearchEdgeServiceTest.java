package com.streamx.blueprints.opensearch.sink;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.data.IndexableResourceFragment;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchEdgeServiceTest extends SearchEdgeServiceTestBase {

  @BeforeAll
  void setupTestOpensearch() {
    Awaitility.setDefaultPollInterval(10, TimeUnit.MILLISECONDS);
    Awaitility.setDefaultPollDelay(10, TimeUnit.MILLISECONDS);
  }

  @AfterAll
  void reset() {
    Awaitility.reset();
  }

  @Test
  void shouldAccessSearchResultsFromReceivedIndexableResources() {
    var firstResource = new ExampleIndexableResourceContent("Title1", "Content1");
    var firstResourceFragments = List.of("fragment");
    validateNoSearchResultsForTestKey();
    validateNoSearchResultsFor(firstResource);

    publishResource(firstResource, firstResourceFragments);
    validateSearchResultsFor(firstResource);

    var secondResource = new ExampleIndexableResourceContent("title2", "content2");
    publishResource(secondResource);
    validateNoSearchResultsFor(firstResource);
    validateSearchResultsFor(secondResource);

    unpublishResource();
    validateNoSearchResultsFor(secondResource);
    validateNoSearchResultsFor(firstResource);
    validateNoSearchResultsForTestKey();
  }

  @ValueSource(strings = {
      "title spa",
      "content ot",
      "fragment sep",
      "TItLe",
      "tit"
  })
  @ParameterizedTest
  void shouldTreatWhitespaceAsPhraseSeparatorAndLastTermMayBePrefixed(String phrase) {
    var firstFragment = new IndexableResourceFragment(
        "{\"content\":\"fragment separated\"}", "any-type");
    publishFragment(firstFragment);
    await().until(() -> getSearchResultByQuery(phrase), VALIDATE_NO_RESULTS);

    var firstResource = new ExampleIndexableResourceContent(
        "Title with space", "Content and other");
    var firstResourceFragments = List.of(TEST_FRAGMENT_KEY);
    publishResource(firstResource, firstResourceFragments);
    Predicate<ExtractableResponse<Response>> resultsPresent = response ->
        totalHits(response) > 0;
    await().until(() -> getSearchResultByQuery(phrase), resultsPresent);

    unpublishResource();
    unpublishFragment();
    await().until(() -> getSearchResultByQuery(phrase), VALIDATE_NO_RESULTS);
  }

  @Test
  void shouldAccessSearchResultsByType() {
    var type = "customType";
    validateNoSearchByType(type);

    var firstResource = new ExampleIndexableResourceContent("Title1", "Content1");
    publishResource(firstResource, type);
    validateNotEmptySearchByTypeResults(type);
    validateNotEmptySearchByTypeResults(type.toLowerCase());

    var prefix = type.substring(0, 5);
    validateNotEmptySearchByTypeResults(prefix);

    var secondResource = new ExampleIndexableResourceContent("title2", "content2");
    publishResource(secondResource);
    validateNoSearchByType(type);
    validateNotEmptySearchByTypeResults(TEST_TYPE);

    unpublishResource();
    validateNoSearchByType(type);
    validateNoSearchByType(TEST_TYPE);
  }

  @Test
  void shouldAddNamespace() {
    validateNoSearchByData("*", "*", null);
    var resource = new ExampleDataContent("id", "category");
    publishResource(resource, (String) null);
    validateNoSearchByData("*", "*", null);
    publishResource(resource, "some-type");
    validateNotEmptySearchByData("*", "*", null);
    unpublishResource();
    validateNoSearchByData("*", "*", null);
  }

  @Test
  void shouldNotIndexResourceWithoutTypeByDefault() {
    validateNoSearchByData("*", "*", null);
    var resource = new ExampleDataContent("id", "category");
    publishResource(resource, (String) null);
    validateNoSearchByData("*", "*", null);
    publishResource(resource, "some-type");
    validateNotEmptySearchByData("*", "*", null);
    unpublishResource();
    validateNoSearchByData("*", "*", null);
  }

  @Test
  void shouldReturn404ForNonExistingSearch() {
    given()
        .basePath("/search/non-existing-search")
        .when()
        .get()
        .then()
        .statusCode(404)
        .extract();
  }

  @Test
  void shouldSearchData() {
    var id = "id";
    var category = "category";
    var type = "customType";
    validateNoSearchByData(id, category, type);

    var firstResource = new ExampleDataContent(id, category);
    publishResource(firstResource, type);
    validateNotEmptySearchByData(id, null, null);
    validateNotEmptySearchByData(null, category, null);
    validateNotEmptySearchByData(null, null, type);
    validateNotEmptySearchByData("*", "*", null);
    validateNotEmptySearchByData(id, category, type);

    var prefix = type.substring(0, 5);
    validateNotEmptySearchByData(null, null, prefix);

    var id2 = "id2";
    var secondResource = new ExampleDataContent(id2, category);
    publishResource(secondResource);
    validateNoSearchByData(id, category, type);
    validateNotEmptySearchByData(id2, category, TEST_TYPE);

    unpublishResource();
    validateNoSearchByData("*", "*", null);
  }

  @Test
  void shouldUpdateFragments() {
    var fragment = "{\"content\":\"fragment\"}";
    validateNoSearchResultsForTestKey();
    validateNoSearchResultsFor(fragment);

    var firstResource = new ExampleIndexableResourceContent("Title1", "Content1");
    var firstResourceFragments = List.of(TEST_FRAGMENT_KEY);
    publishResource(firstResource, firstResourceFragments);
    validateSearchResultsFor(firstResource);
    validateNoFragmentSearchResults("fragment");

    var firstFragment = new IndexableResourceFragment(fragment, "any-type");
    publishFragment(firstFragment);

    validateFragmentSearchResults("Fragment");

    var updatedFragment = "{\"content\":\"updatedfragment\"}";
    var secondFragment = new IndexableResourceFragment(updatedFragment, "any-type");
    publishFragment(secondFragment);

    validateNoFragmentSearchResults("fragment");
    validateFragmentSearchResults("updatedfragment");

    unpublishFragment();

    validateSearchResultsFor(firstResource);
    validateNoFragmentSearchResults("updatedfragment");

    unpublishResource();
    validateNoSearchResultsFor(firstResource);
    validateNoSearchResultsForTestKey();
  }

  @Test
  void shouldPublishResourcesWithFragmentExistingInStore() {
    var fragment = "{\"content\":\"fragment\"}";
    validateNoSearchResultsForTestKey();
    validateNoSearchResultsFor(fragment);

    var firstFragment = new IndexableResourceFragment(fragment, "any-type");
    publishFragment(firstFragment);

    var firstResource = new ExampleIndexableResourceContent("Title1", "Content1");
    var firstResourceFragments = List.of(TEST_FRAGMENT_KEY);
    publishResource(firstResource, firstResourceFragments);
    validateSearchResultsFor(firstResource);
    validateFragmentSearchResults("fragment");

    unpublishFragment();
    unpublishResource();
    validateNoSearchResultsFor(firstResource);
    validateNoSearchResultsForTestKey();
  }


}
