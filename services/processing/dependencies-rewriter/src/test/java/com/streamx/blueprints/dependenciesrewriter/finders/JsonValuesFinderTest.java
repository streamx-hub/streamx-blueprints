package com.streamx.blueprints.dependenciesrewriter.finders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonValuesFinderTest {

  private static final JsonValuesFinder jsonValuesFinder = new JsonValuesFinder();

  // values for current test
  private String json;
  private List<String> jsonPaths;
  private Set<String> foundValues;
  private Exception exception;

  @Test
  void shouldFindAllImagePaths() {
    givenJson("""
        {
          "columns": [ "path", "title", "image" ],
          "data": [
            {
              "path": "/en/nav",
              "title": "Podcast Episodes",
              "image": "/en/media_13aa.png?width=1200&format=jpg"
            },
            {
              "path": "/en/feed-blocks",
              "title": "recent article feed",
              "image": "/default-meta-image.png?width=1200&format=jpg"
            },
            {
              "path": "/en/podcast/adobe-eds",
              "title": "The Adobe EDS",
              "image": "/en/podcast/media_1461.jpg?width=1200&format=jpg"
            }
          ],
          "total": 3
        }
        """)
        .andGivenJsonPath("$.data[*].image")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "/en/media_13aa.png?width=1200&format=jpg",
            "/default-meta-image.png?width=1200&format=jpg",
            "/en/podcast/media_1461.jpg?width=1200&format=jpg"
        );
  }


  @Test
  void shouldFindAllImagePathsAndTitles() {
    givenJson("""
        {
          "data": [
            {
              "path": "path/3",
              "title": "Title 3",
              "image": "image-3.jpg"
            },
            {
              "path": "path/2",
              "title": "Title 2",
              "image": "image-2.jpg"
            },
            {
              "path": "path/1",
              "title": "Title 1",
              "image": "image-1.jpg"
            },
            {
              "path": "path/empty",
              "title": "Title empty",
              "image": ""
            },
            {
              "path": "path/null",
              "title": "Title null",
              "image": null
            }
          ]
        }
        """)
        .andGivenJsonPaths("$.data[*].image", "$.data[*].title")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "image-3.jpg", "image-2.jpg", "image-1.jpg",
            "Title 3", "Title 2", "Title 1", "Title empty", "Title null"
        );
  }

  @Test
  void shouldFindNumericValues() {
    givenJson("{ \"value\": 1 }")
        .andGivenJsonPath("$.value")
        .whenFindMatchingValues()
        .thenExpectFoundValues("1");
  }

  @Test
  void shouldFindNothingWhenNoMatches() {
    givenJson("{ \"value\": 1 }")
        .andGivenJsonPath("$.data[*].image")
        .whenFindMatchingValues()
        .thenExpectFoundValues();
  }

  @Test
  void shouldNotThrowExceptionForInvalidJson() {
    givenJson("Not a JSON")
        .andGivenJsonPath("$")
        .whenFindMatchingValues()
        .thenExpectFoundValue("Not a JSON");
  }

  @Test
  void shouldNotThrowExceptionForInvalidJsonPath() {
    givenJson("{}")
        .andGivenJsonPath("$.data[?(@.price > )]\n")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  private JsonValuesFinderTest givenJson(String json) {
    this.json = json;
    return this;
  }

  private JsonValuesFinderTest andGivenJsonPath(String jsonPath) {
    this.jsonPaths = List.of(jsonPath);
    return this;
  }

  private JsonValuesFinderTest andGivenJsonPaths(String... jsonPaths) {
    this.jsonPaths = List.of(jsonPaths);
    return this;
  }

  private JsonValuesFinderTest whenFindMatchingValues() {
    try {
      this.foundValues = jsonValuesFinder.findMatchingValues(json, jsonPaths);
    } catch (Exception ex) {
      this.exception = ex;
    }
    return this;
  }

  private void thenExpectFoundValue(String expectedValue) {
    assertThat(exception).isNull();
    assertThat(foundValues).containsOnly(expectedValue);
  }

  private void thenExpectFoundValues(String... expectedValues) {
    assertThat(exception).isNull();
    assertThat(foundValues).containsExactly(expectedValues);
  }

  private void thenExpectNoFoundValues() {
    assertThat(exception).isNull();
    assertThat(foundValues).isEmpty();
  }

}