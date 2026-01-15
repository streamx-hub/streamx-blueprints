package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(value = AggregateByPropertyValueCollectorFilterByCategoryTest.Configuration.class)
class AggregateByPropertyValueCollectorFilterByCategoryTest
    extends AbstractAggregateByPropertyValueCollectorTest {

  @Test
  void shouldFilterProductsByCategory() {
    publishData(PRODUCT_1_ID);
    publishData(PRODUCT_2_ID);
    publishData(PRODUCT_3_ID);

    waitForReceivedDataEvents(2);

    JsonNode jsonNode1 = readReceivedData(0);
    assertKey(jsonNode1, "collected:products:cheapest-by-category:End_Tables");
    assertProductIds(jsonNode1, PRODUCT_1_ID, PRODUCT_3_ID);
    assertPrice(jsonNode1, 0, 670);
    assertPrice(jsonNode1, 1, 700);

    JsonNode jsonNode2 = readReceivedData(1);
    assertKey(jsonNode2, "collected:products:cheapest-by-category:Featured_products");
    assertProductId(jsonNode2, PRODUCT_1_ID);
  }

  private static void assertPrice(JsonNode dataNode, int valueIndex, int expectedPrice) {
    assertThat(dataNode.get("values").get(valueIndex).get("price").get("value").intValue())
        .isEqualTo(expectedPrice);
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "aggregatetest,case4";
    }
  }

}
