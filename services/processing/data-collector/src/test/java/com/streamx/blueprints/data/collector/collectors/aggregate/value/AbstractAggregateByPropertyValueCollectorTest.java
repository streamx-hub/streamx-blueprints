package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.Channels;
import com.streamx.blueprints.data.collector.Channels.Outgoing;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractAggregateByPropertyValueCollectorTest {

  protected static final String PRODUCT_1_ID = "B072ZLCB3M";
  protected static final String PRODUCT_2_ID = "B07TMH6289";
  protected static final String PRODUCT_3_ID = "B07DBGJ3TF";

  private static final Map<String, String> products = Map.of(
      PRODUCT_1_ID, contentOf(new File("src/test/resources/products/products1.json")),
      PRODUCT_2_ID, contentOf(new File("src/test/resources/products/products2.json")),
      PRODUCT_3_ID, contentOf(new File("src/test/resources/products/products3.json"))
  );

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  @Any
  InMemoryConnector connector;

  private InMemorySource<CloudEvent> dataSource;
  private InMemorySink<CloudEvent> dataSink;

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.Incoming.DATA);
    dataSink = connector.sink(Outgoing.COLLECTED_DATA);
  }

  protected void publishData(String productId) {
    publishData(productId, null);
  }

  protected void publishData(String productId, String dataType) {
    CloudEvent event = CloudEventUtils.eventWithData(
        "product:" + productId,
        Data.TYPE_PUBLISHED,
        new Data(products.get(productId), dataType),
        CloudEventUtils.toOffsetDateTime(1)
    );
    dataSource.send(event);
  }

  protected void waitForReceivedDataEvents(int expectedCount) {
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(dataSink.received()).hasSize(expectedCount));
  }

  protected JsonNode readReceivedData(int indexInDataSink) {
    CloudEvent event = dataSink.received().get(indexInDataSink).getPayload();
    Data data = CloudEventUtils.getData(event, Data.class);
    assertThat(data).isNotNull();
    try {
      return objectMapper.readTree(data.getContentAsString());
    } catch (JsonProcessingException e) {
      return fail("Error converting resource content", e);
    }
  }

  protected static void assertKey(JsonNode jsonNode, String expectedKey) {
    String actualKey = jsonNode.get("key").textValue();
    assertThat(actualKey).isEqualTo(expectedKey);
  }

  protected static void assertProductId(JsonNode jsonNode, String expectedProductId) {
    assertProductIds(jsonNode, expectedProductId);
  }

  protected static void assertProductIds(JsonNode jsonNode, String... expectedProductIds) {
    ArrayNode idNodes = JsonPath.parse(jsonNode).read("$.values[*].id", ArrayNode.class);
    assertThat(idNodes)
        .extracting(JsonNode::textValue)
        .containsExactly(expectedProductIds);
  }
}
