package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(value = AggregateByPropertyValueCollectorDataFilterTypeTest.Configuration.class)
public class AggregateByPropertyValueCollectorDataFilterTypeTest extends
    AbstractAggregateByPropertyValueCollectorTest {

  private static final String TEST_TYPE = "test-type";

  @Test
  void shouldFilterProductsByCategory() {
    publishData(PRODUCT_1_ID);
    publishData(PRODUCT_2_ID);
    publishData(PRODUCT_3_ID, TEST_TYPE);

    waitForReceivedDataEvents(1);
    JsonNode jsonNode = readReceivedData(0);

    assertKey(jsonNode, "collected:products:cheapest-by-category:End_Tables");
    assertProductId(jsonNode, PRODUCT_3_ID);
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "streamx.blueprints.data-collector.configurations"
              + ".cheapest-products-by-category.data-type-match-pattern",
          TEST_TYPE
      );
    }

    @Override
    public String getConfigProfile() {
      return "aggregatetest";
    }
  }
}
