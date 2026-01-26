package com.streamx.blueprints.json.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.json.aggregator.JsonAggregatorIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class JsonAggregatorIT extends BaseQuarkusIntegrationTest {

  private static final String PRODUCT_OUTPUT_TYPE = "product/simple";
  private static final String REVIEW_OUTPUT_TYPE = "reviews/review";
  private static final String ANY = "any";

  @Test
  void shouldAggregateData() {
    // when: publish product
    sendDataEvent("pim:1", "{\"id\":\"1\"}");

    // then
    CloudEvent outgoingEvent1 = waitForResponseEvent(Channels.AGGREGATED_DATA);
    assertOutgoingEvent(outgoingEvent1,
        "product:1",
        "{\"id\":\"1\"}",
        PRODUCT_OUTPUT_TYPE
    );

    // when: publish price for that product
    sendDataEvent("price:1", "{\"price\":\"200\"}");

    // then
    CloudEvent outgoingEvent2 = waitForLastResponseEvent(Channels.AGGREGATED_DATA, 2);
    assertOutgoingEvent(outgoingEvent2,
        "product:1",
        "{\"id\":\"1\",\"price\":\"200\"}",
        PRODUCT_OUTPUT_TYPE
    );
  }

  @Test
  void shouldAggregateMultivaluedData() {
    // when
    sendMultivaluedDataEvent("review:1:john", "{\"rating\":\"good\"}");
    sendMultivaluedDataEvent("review:1:alice", "{\"rating\":\"bad\"}");

    // then
    CloudEvent outgoingEvent = waitForLastResponseEvent(Channels.AGGREGATED_MULTIVALUED_DATA, 2);
    assertOutgoingEvent(outgoingEvent,
        "reviews:1",
        "{\"reviews\":[{\"rating\":\"bad\"},{\"rating\":\"good\"}]}",
        REVIEW_OUTPUT_TYPE
    );
  }

  private void sendDataEvent(String subject, String content) {
    Data data = new Data(content, ANY);
    CloudEvent event = CloudEventUtils.eventWithData(subject, Data.TYPE_PUBLISHED, data);
    sendStatefulEvent(event, Channels.DATA_STATE, Channels.DATA);
  }

  private void sendMultivaluedDataEvent(String subject, String content) {
    Data data = new Data(content, ANY);
    CloudEvent event = CloudEventUtils.eventWithData(subject, Data.TYPE_PUBLISHED, data);
    sendStatefulEvent(event, Channels.MULTIVALUED_DATA_STATE, Channels.MULTIVALUED_DATA);
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent,
      String expectedDataKey, String expectedDataContent, String expectedDataType) {
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("json-aggregator");
    assertThat(outgoingEvent.getType()).isEqualTo(Data.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getSubject()).isEqualTo(expectedDataKey);

    var outgoingData = CloudEventUtils.getData(outgoingEvent, Data.class);
    assertThat(outgoingData).isNotNull();
    assertThat(outgoingData.getType()).isEqualTo(expectedDataType);
    assertThat(outgoingData.getContentAsString()).isEqualTo(expectedDataContent);
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      String basePath = "streamx.blueprints.json-aggregator.";
      return Map.of(
          basePath + "configurations[0].master-namespace", "pim",
          basePath + "configurations[0].optional-namespaces", "price",
          basePath + "configurations[0].output-namespace", "product",
          basePath + "configurations[0].output-type", PRODUCT_OUTPUT_TYPE,
          basePath + "configurations[1].master-namespace", "review",
          basePath + "configurations[1].output-namespace", "reviews",
          basePath + "configurations[1].output-type", REVIEW_OUTPUT_TYPE
      );
    }
  }
}