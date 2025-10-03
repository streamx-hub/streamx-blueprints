package com.streamx.blueprints.rewriter.finders;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.logging.Log;
import java.util.List;
import java.util.Set;

abstract class AbstractValuesFinderTest<T extends AbstractValuesFinderTest<T>> {

  private String input;
  private List<String> lookupPaths;
  private Set<String> values;
  private Exception exception;

  protected abstract BaseValuesFinder getFinder();

  protected T givenInput(String input) {
    this.input = input;
    return (T) this;
  }


  protected T andGivenPath(String path) {
    this.lookupPaths = List.of(path);
    return (T) this;
  }

  protected T andGivenLookupPaths(String... paths) {
    this.lookupPaths = List.of(paths);
    return (T) this;
  }

  protected T whenFindMatchingValues() {
    try {
      this.values = getFinder().findMatchingValues(input, lookupPaths);
    } catch (Exception ex) {
      Log.warn("Exception", ex);
      this.exception = ex;
    }
    return (T) this;
  }

  protected void thenExpectFoundValue(String expectedValue) {
    assertThat(exception).isNull();
    assertThat(values).containsOnly(expectedValue);
  }

  protected void thenExpectFoundValues(String... expectedValues) {
    assertThat(exception).isNull();
    assertThat(values).containsExactly(expectedValues);
  }

  protected void thenExpectNoFoundValues() {
    assertThat(exception).isNull();
    assertThat(values).isEmpty();
  }

}
