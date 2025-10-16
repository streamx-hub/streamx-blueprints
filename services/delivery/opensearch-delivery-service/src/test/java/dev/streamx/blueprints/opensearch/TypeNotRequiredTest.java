package dev.streamx.blueprints.opensearch;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(TypeNotRequiredProfile.class)
public class TypeNotRequiredTest extends SearchDeliveryServiceTestBase {

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
