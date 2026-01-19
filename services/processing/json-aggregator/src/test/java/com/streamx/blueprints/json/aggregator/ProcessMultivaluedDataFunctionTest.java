package com.streamx.blueprints.json.aggregator;

import com.streamx.blueprints.test.unit.StatefulInMemorySource;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessMultivaluedDataFunctionTest extends ProcessDataFunctionBaseTest {

  private static final String REVIEW_PAYLOAD = "{\"stars\":1,\"text\":\"nice\"}";

  @Inject
  @Any
  InMemoryConnector connector;

  StatefulInMemorySource dataSource;

  InMemorySink<CloudEvent> multiValuedSink;

  @Override
  protected InMemorySink<CloudEvent> getDataSink() {
    return multiValuedSink;
  }

  @Override
  protected StatefulInMemorySource getDataSource() {
    return dataSource;
  }

  @BeforeEach
  void beforeEach() {
    dataSource = new StatefulInMemorySource(connector,
        Channels.MULTIVALUED_DATA, Channels.MULTIVALUED_DATA_STATE);
    multiValuedSink = connector.sink(Channels.AGGREGATED_MULTIVALUED_DATA);
    multiValuedSink.clear();
  }

  @Test
  void expectReviewsArrayWithSingleReviewAndRewrittenType() {
    int id = 100;
    publish("typed-review:" + id + ":hash", "my-review-type", REVIEW_PAYLOAD);
    waitForProcessedMessages(1);
    assertReviewsPublished(
        "typed-reviews:" + id,
        "my-review-type",
        "{\"typed-reviews\":[{\"stars\":1,\"text\":\"nice\"}]}"
    );
  }

  @Test
  void expectReviewsArrayWithSingleReviewAndTypeFromConfig() {
    int id = 101;
    publish("review:" + id + ":hash", "my-review-type", REVIEW_PAYLOAD);
    waitForProcessedMessages(1);
    assertReviewsPublished(
        "reviews:" + id,
        "reviews/all",
        "{\"reviews\":[{\"stars\":1,\"text\":\"nice\"}]}"
    );
  }

  @Test
  void expectReviewsArrayWithTwoReviews() {
    int id = 102;
    publish("review:" + id + ":hash", REVIEW_PAYLOAD);
    publish("review:" + id + ":hash1", REVIEW_PAYLOAD);
    waitForProcessedMessages(2);
    assertReviewsPublished(
        "reviews:" + id,
        "reviews/all",
        "{\"reviews\":[{\"stars\":1,\"text\":\"nice\"},{\"stars\":1,\"text\":\"nice\"}]}"
    );
  }

  @Test
  void expectReviewsArrayWithOneReviewWhenEmpty() {
    int id = 103;
    publish("review:" + id + ":hash", REVIEW_PAYLOAD);
    unpublish("review:" + id + ":hash1");
    waitForProcessedMessages(2);
    assertReviewsPublished(
        "reviews:" + id,
        "reviews/all",
        "{\"reviews\":[{\"stars\":1,\"text\":\"nice\"}]}"
    );
  }

  @Test
  void expectMultipleOutputsToBeProduced() {
    publish("multi-outputs-trigger:1:hash", REVIEW_PAYLOAD);
    waitForProcessedMessages(2);

    assertReviewsPublished(
        "multi-output-1:1",
        null,
        "{\"multi-output-1\":[{\"stars\":1,\"text\":\"nice\"}]}"
    );
    assertReviewsPublished(
        "multi-output-2:1",
        null,
        "{\"multi-output-2\":[{\"stars\":1,\"text\":\"nice\"}]}"
    );
  }

  @Test
  void shouldNotProcessMessageWithInvalidKey() {
    publish("multi-outputs-trigger:1", REVIEW_PAYLOAD);
    assertNoResultMessageWasSent();
  }

  private void assertReviewsPublished(String key, String expectedType, String expectedPayload) {
    assertPublished(key, expectedType, expectedPayload);
  }

}
