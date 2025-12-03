package com.streamx.blueprints.opensearch.sink;

import static org.mockito.Mockito.doReturn;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TypeNotRequiredTest extends SearchServiceTestBase {

  @InjectSpy
  SearchServiceSink sink;

  @BeforeEach
  void configureService() {
    doReturn(false).when(sink).isTypeRequired();
  }

  @Test
  void shouldIndexResourceWithoutType() {
    var resource = new ExampleDataContent("id", "category");

    validateNoSearchByData("*", "*", null);
    publishResource(resource, (String) null);
    validateNotEmptySearchByData("*", "*", null);
    unpublishResource();
    validateNoSearchByData("*", "*", null);
    publishResource(resource, "some-type");
    validateNotEmptySearchByData("*", "*", null);
    unpublishResource();
    validateNoSearchByData("*", "*", null);
  }
}
