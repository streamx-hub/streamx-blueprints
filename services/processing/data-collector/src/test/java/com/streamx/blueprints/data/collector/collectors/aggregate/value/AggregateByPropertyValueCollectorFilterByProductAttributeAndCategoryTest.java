package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.data.collector.collectors.aggregate.value.AggregateByPropertyValueCollectorFilterByProductAttributeAndCategoryTest.Configuration;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(value = Configuration.class)
class AggregateByPropertyValueCollectorFilterByProductAttributeAndCategoryTest
    extends AbstractAggregateByPropertyValueCollectorTest {

  @Test
  void shouldFilterProductsByAttributeAndCategory() {
    publishData(PRODUCT_1_ID);
    publishData(PRODUCT_2_ID);
    publishData(PRODUCT_3_ID);

    waitForReceivedDataEvents(2);
    JsonNode jsonNode1 = readReceivedData(0);
    JsonNode jsonNode2 = readReceivedData(1);

    assertKey(jsonNode1, "collected:products:cheapest-by-category:End_Tables");
    assertProductId(jsonNode1, PRODUCT_1_ID);

    assertKey(jsonNode2, "collected:products:cheapest-by-category:Featured_products");
    assertProductId(jsonNode2, PRODUCT_1_ID);
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "aggregatetest,case3";
    }
  }

}
