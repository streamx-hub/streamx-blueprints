package com.streamx.blueprints.rewriter.finders;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.logging.Log;
import java.util.List;
import java.util.Set;

abstract class AbstractValuesFinderTest {

  private String input;
  private List<String> lookupSelectors;
  private Set<String> foundValues;
  private Exception thrownException;

  protected abstract BaseValuesFinder getFinder();

  protected AbstractValuesFinderTest givenInput(String input) {
    this.input = input;
    return this;
  }

  protected AbstractValuesFinderTest andGivenLookupSelector(String selector) {
    this.lookupSelectors = List.of(selector);
    return this;
  }

  protected AbstractValuesFinderTest andGivenLookupSelectors(String... selectors) {
    this.lookupSelectors = List.of(selectors);
    return this;
  }

  protected AbstractValuesFinderTest whenFindMatchingValues() {
    try {
      this.foundValues = getFinder().findMatchingValues(input, lookupSelectors);
    } catch (Exception ex) {
      Log.warn("Exception", ex);
      this.thrownException = ex;
    }
    return this;
  }

  protected void thenExpectFoundValue(String expectedValue) {
    assertThat(thrownException).isNull();
    assertThat(foundValues).containsOnly(expectedValue);
  }

  protected void thenExpectFoundValues(String... expectedValues) {
    assertThat(thrownException).isNull();
    assertThat(foundValues).containsExactly(expectedValues);
  }

  protected void thenExpectNoFoundValues() {
    assertThat(thrownException).isNull();
    assertThat(foundValues).isEmpty();
  }

}
