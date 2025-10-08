package com.streamx.blueprints.rewriter.finders;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.logging.Log;
import java.util.List;
import java.util.Set;

abstract class AbstractValuesFinderTest<T extends AbstractValuesFinderTest<T>> {

  private String input;
  private List<String> lookupSelectors;
  private Set<String> foundValues;
  private Exception thrownException;

  protected abstract BaseValuesFinder getFinder();

  protected T givenInput(String input) {
    this.input = input;
    return (T) this;
  }


  protected T andGivenLookupSelector(String selector) {
    this.lookupSelectors = List.of(selector);
    return (T) this;
  }

  protected T andGivenLookupSelectors(String... selectors) {
    this.lookupSelectors = List.of(selectors);
    return (T) this;
  }

  protected T whenFindMatchingValues() {
    try {
      this.foundValues = getFinder().findMatchingValues(input, lookupSelectors);
    } catch (Exception ex) {
      Log.warn("Exception", ex);
      this.thrownException = ex;
    }
    return (T) this;
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
