package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(value = AggregateByPropertyValueCollectorDataFilterKeyTest.Configuration.class)
public class AggregateByPropertyValueCollectorDataFilterKeyTest extends
    AbstractAggregateByPropertyValueCollectorTest {

  @Test
  void shouldFilterProductsByCategory() {
    publishData(PRODUCT_1_ID);
    publishData(PRODUCT_2_ID);
    publishData(PRODUCT_3_ID);

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
              + ".cheapest-products-by-category.data-key-match-pattern",
          "product:" + PRODUCT_3_ID
      );
    }

    @Override
    public String getConfigProfile() {
      return "aggregatetest";
    }
  }
}
