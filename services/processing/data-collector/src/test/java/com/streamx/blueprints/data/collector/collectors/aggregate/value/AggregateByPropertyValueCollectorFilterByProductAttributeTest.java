package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import com.streamx.blueprints.data.collector.Channels;
import com.streamx.blueprints.data.collector.Channels.Outgoing;
import com.streamx.blueprints.data.collector.collectors.aggregate.value.AggregateByPropertyValueCollectorFilterByProductAttributeTest.Configuration;
import dev.streamx.blueprints.data.Data;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Set;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(value = Configuration.class)
class AggregateByPropertyValueCollectorFilterByProductAttributeTest
    extends AbstractAggregateByPropertyValueCollector {

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  @Any
  com.streamx.blueprints.data.collector.Collectors collectors;

  InMemorySource<Message<Data>> dataSource;
  InMemorySink<Data> webResourceSink;

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.Incoming.DATA);
    webResourceSink = connector.sink(Outgoing.COLLECTED_DATA);
  }

  @Test
  void shouldFilterProductsByAttribute() throws IOException {
    Message<Data> data1 = getDataMessage(PRODUCT_1_ID);
    Message<Data> data2 = getDataMessage(PRODUCT_2_ID);
    Message<Data> data3 = getDataMessage(PRODUCT_3_ID);
    dataSource.send(data1);
    dataSource.send(data2);
    dataSource.send(data3);
    await().until(() -> webResourceSink.received().size() == 2);

    JsonNode jsonNode1 = OBJECT_MAPPER.readTree(
        webResourceSink.received().get(0).getPayload().getContentAsString());
    JsonNode jsonNode2 = OBJECT_MAPPER.readTree(
        webResourceSink.received().get(1).getPayload().getContentAsString());

    assertThat(jsonNode1.get("key").textValue())
        .isEqualTo("collected:products:cheapest-by-category:Featured_products");
    assertThat(JsonPath.parse(jsonNode1).read("$.values[*].id", ArrayNode.class).size())
        .isEqualTo(1);
    JsonPath.parse(jsonNode1).read("$.values[*].id", ArrayNode.class)
        .forEach(jsonNode -> assertTrue(Set.of("B072ZLCB3M")
            .contains(jsonNode.textValue())));

    assertThat(jsonNode2.get("key").textValue())
        .isEqualTo("collected:products:cheapest-by-category:End_Tables");
    assertThat(JsonPath.parse(jsonNode2).read("$.values[*].id", ArrayNode.class).size())
        .isEqualTo(1);
    JsonPath.parse(jsonNode2).read("$.values[*].id", ArrayNode.class)
        .forEach(jsonNode -> assertTrue(Set.of("B072ZLCB3M")
            .contains(jsonNode.textValue())));
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "aggregatetest,case2";
    }
  }

}
