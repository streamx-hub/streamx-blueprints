package com.streamx.blueprints.data.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.DataCollectorIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class DataCollectorIT extends BaseQuarkusIntegrationTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final String INPUT_PRODUCT_TYPE = "product/simple";
  private static final String OUTPUT_DATA_TYPE = "collected-data";

  private static final String PRODUCT_1_ID = "Table";
  private static final String PRODUCT_1_JSON = """
      {
        "id": "Table",
        "name": "Ravenna Home Open Storage Table",
        "categories": [ { "name": "Furniture" } ],
        "price": { "value": 700 }
      }
      """;

  private static final String PRODUCT_2_ID = "Recliner";
  private static final String PRODUCT_2_JSON = """
      {
        "id": "Recliner",
        "name": "LeatherSoft Recliner with Armrest Storage",
        "categories": [ { "name": "Furniture" } ],
        "price": { "value": 680 }
      }
      """;

  private static final String PRODUCT_3_ID = "Chair";
  private static final String PRODUCT_3_JSON = """
      {
        "id": "Chair",
        "name": "Rivet Bristol Natural Chair",
        "categories": [ { "name": "Furniture" } ],
        "price": { "value": 670 }
      }
      """;

  @Override
  protected Duration waitForResponseEventsTimeout() {
    return Duration.ofSeconds(7);
  }

  @Test
  void shouldAggregateData() throws IOException {
    // when: publish products
    sendDataEvent("product:" + PRODUCT_1_ID, PRODUCT_1_JSON);
    sendDataEvent("product:" + PRODUCT_2_ID, PRODUCT_2_JSON);
    sendDataEvent("product:" + PRODUCT_3_ID, PRODUCT_3_JSON);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.Outgoing.COLLECTED_DATA);

    assertThat(outgoingEvent.getSource()).asString().isEqualTo("data-collector");
    assertThat(outgoingEvent.getType()).isEqualTo(Data.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getSubject()).isEqualTo("cheapest-by-category_Furniture");

    Data outgoingData = CloudEventUtils.getData(outgoingEvent, Data.class);
    assertThat(outgoingData).isNotNull();
    assertThat(outgoingData.getType()).isEqualTo(OUTPUT_DATA_TYPE);
    assertThat(formatJson(outgoingData.getContentAsString())).isEqualTo(formatJson(
        "{"
        + "  \"key\": \"cheapest-by-category_Furniture\","
        + "  \"values\": [" + PRODUCT_3_JSON + "," + PRODUCT_2_JSON + "," + PRODUCT_1_JSON + "]"
        + "}"));
  }

  private void sendDataEvent(String subject, String content) throws IOException {
    Data data = new Data(content, INPUT_PRODUCT_TYPE);
    CloudEvent event = CloudEventUtils.eventWithData(subject, Data.TYPE_PUBLISHED, data);
    sendEvent(event, Channels.Incoming.DATA);
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      String basePath = "streamx.blueprints.data-collector.";
      String dirtyCheck = basePath + "dirty-check.";
      String cheapestProducts = basePath + "configurations.\"cheapest-products-by-category\".";

      return Map.of(
          dirtyCheck + "max-dirty-sequence-count", "0", // send data as soon as collected
          dirtyCheck + "delay", "3s", // delay starting trigger until emitter is injected
          dirtyCheck + "interval", "1s",
          cheapestProducts + "data-key-match-pattern", "product:.*",
          cheapestProducts + "collector", "aggregate-by-property-value",
          cheapestProducts + "output-data-type", OUTPUT_DATA_TYPE,
          cheapestProducts + "properties.output-key-prefix", "cheapest-by-category_",
          cheapestProducts + "properties.group-by", "categories/name",
          cheapestProducts + "properties.sort-by", "price/value",
          cheapestProducts + "properties.sort-mode", "asc"
      );
    }
  }

  private static String formatJson(String json) throws JsonProcessingException {
    JsonNode tree = objectMapper.readTree(json);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
  }
}