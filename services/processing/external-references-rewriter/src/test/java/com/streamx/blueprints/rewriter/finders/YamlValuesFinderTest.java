package com.streamx.blueprints.rewriter.finders;

import org.junit.jupiter.api.Test;

class YamlValuesFinderTest extends AbstractValuesFinderTest<YamlValuesFinderTest> {

  private static final YamlValuesFinder yamlValuesFinder = new YamlValuesFinder();

  @Test
  void shouldFindAllTargetPaths() {
    givenInput("""
        indices:
          index-en:
            include:
              - /en/**
            exclude:
              - /en/drafts/**
            target: /en-index.json
            properties:
          ## Polish CONTENT
          index-pl:
            include:
              - /pl/**
            exclude:
              - /pl/drafts/**
            target: /pl-index.json
            properties:
        """)
        .andGivenLookupPaths("$.indices.[*].target")
        .whenFindMatchingValues()
        .thenExpectFoundValues("/en-index.json", "/pl-index.json");
  }


  @Test
  void shouldFindNumericValues() {
    givenInput("""
        numbers:
          item1:
            value: 1
          item2:
            value: 2
        """)
        .andGivenLookupPaths("$.numbers.[*].value")
        .whenFindMatchingValues()
        .thenExpectFoundValues("1", "2");
  }

  @Test
  void shouldFindNothingWhenNoMatches() {
    givenInput("""
        numbers:
          item1:
            value: 1
          item2:
            value: 2
        """)
        .andGivenLookupPaths("$.numbers.[*].key")
        .whenFindMatchingValues()
        .thenExpectFoundValues();
  }

  @Test
  void shouldNotThrowExceptionForInvalidJson() {
    givenInput("Not a YAML")
        .andGivenLookupPaths("$")
        .whenFindMatchingValues()
        .thenExpectFoundValue("Not a YAML");
  }

  @Test
  void shouldNotThrowExceptionForInvalidJsonPath() {
    givenInput("")
        .andGivenLookupPaths("$.data[?(@.price > )]\n")
        .whenFindMatchingValues()
        .thenExpectNoFoundValues();
  }

  @Override
  protected BaseValuesFinder getFinder() {
    return yamlValuesFinder;
  }
}