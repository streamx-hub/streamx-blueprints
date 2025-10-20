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
    JsonNode jsonNode2 = readReceivedData(1);

    assertKey(jsonNode1, "collected:products:cheapest-by-category:Featured_products");
    assertProductId(jsonNode1, PRODUCT_1_ID);

    assertKey(jsonNode2, "collected:products:cheapest-by-category:End_Tables");
    assertProductIds(jsonNode2, PRODUCT_1_ID, PRODUCT_3_ID);

    assertThat(jsonNode2.get("values").get(0).get("price").get("value").intValue())
        .isEqualTo(670);
    assertThat(jsonNode2.get("values").get(1).get("price").get("value").intValue())
        .isEqualTo(700);
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "aggregatetest,case4";
    }
  }

}
