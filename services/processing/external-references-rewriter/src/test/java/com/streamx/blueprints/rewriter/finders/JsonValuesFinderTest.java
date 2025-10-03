package com.streamx.blueprints.rewriter.finders;

import org.junit.jupiter.api.Test;

class JsonValuesFinderTest extends AbstractValuesFinderTest<JsonValuesFinderTest> {

  private static final JsonValuesFinder jsonValuesFinder = new JsonValuesFinder();

  @Test
  void shouldFindAllImagePaths() {
    givenInput("""
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
        .andGivenLookupPaths("$.data[*].image")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "/en/media_13aa.png?width=1200&format=jpg",
            "/default-meta-image.png?width=1200&format=jpg",
            "/en/podcast/media_1461.jpg?width=1200&format=jpg"
        );
  }

  @Test
  void shouldFindAllImagePathsAndTitles() {
    givenInput("""
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
        .andGivenLookupPaths("$.data[*].image", "$.data[*].title")
        .whenFindMatchingValues()
        .thenExpectFoundValues(
            "image-3.jpg", "image-2.jpg", "image-1.jpg",
            "Title 3", "Title 2", "Title 1", "Title empty", "Title null"
        );
  }

  @Test
  void shouldFindNumericValues() {
    givenInput("{ \"value\": 1 }")
        .andGivenLookupPaths("$.value")
        .whenFindMatchingValues()
        .thenExpectFoundValues("1");
  }

  @Test
  void shouldFindNothingWhenNoMatches() {
    givenInput("{ \"value\": 1 }")
        .andGivenLookupPaths("$.data[*].image")
        .whenFindMatchingValues()
        .thenExpectFoundValues();
  }

  @Test
  void shouldNotThrowExceptionForInvalidJson() {
    givenInput("Not a JSON")
        .andGivenLookupPaths("$")
        .whenFindMatchingValues()
        .thenExpectFoundValue("Not a JSON");
  }

  @Test
  void shouldNotThrowExceptionForInvalidJsonPath() {
    givenInput("{}")
        .andGivenLookupPaths("$.data[?(@.price > )]\n")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  @Override
  protected BaseValuesFinder getFinder() {
    return jsonValuesFinder;
  }

}