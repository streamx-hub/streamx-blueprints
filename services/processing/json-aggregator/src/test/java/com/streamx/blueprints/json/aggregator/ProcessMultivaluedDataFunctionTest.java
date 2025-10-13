package com.streamx.blueprints.json.aggregator;

import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ProcessMultivaluedDataFunctionTest extends ProcessDataFunctionBaseTest {

  private static final String REVIEW_PAYLOAD = "{\"stars\":1,\"text\":\"some review\"}";

  @Inject
  @Any
  InMemoryConnector connector;

  InMemorySource<CloudEvent> dataSource;

  InMemorySink<CloudEvent> multiValuedSink;

  @Override
  protected InMemorySink<CloudEvent> getDataSink() {
    return multiValuedSink;
  }

  @Override
  protected InMemorySource<CloudEvent> getDataSource() {
    return dataSource;
  }

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.MULTIVALUED_DATA);
    multiValuedSink = connector.sink(Channels.AGGREGATED_MULTIVALUED_DATA);
    multiValuedSink.clear();
  }

  @Test
  void expectReviewsArrayWithSingleReview() {
    int id = 101;
    publish("review:" + id + ":hash", REVIEW_PAYLOAD);
    waitForProcessedMessages(1);
    assertReviewsPublished(id, "{\"reviews\":[{\"stars\":1,\"text\":\"some review\"}]}");
  }

  @Test
  void expectReviewsArrayWithTwoReviews() {
    int id = 102;
    publish("review:" + id + ":hash", REVIEW_PAYLOAD);
    publish("review:" + id + ":hash1", REVIEW_PAYLOAD);
    waitForProcessedMessages(2);
    assertReviewsPublished(id, "{\"reviews\":[{\"stars\":1,"
        + "\"text\":\"some review\"},{\"stars\":1,\"text\":\"some review\"}]}");
  }

  @Test
  void expectReviewsArrayWithOneReviewWhenEmpty() {
    int id = 103;
    publish("review:" + id + ":hash", REVIEW_PAYLOAD);
    unpublish("review:" + id + ":hash1");
    waitForProcessedMessages(2);
    assertReviewsPublished(id, "{\"reviews\":[{\"stars\":1,\"text\":\"some review\"}]}");
  }

  @Test
  void expectMultipleOutputsToBeProduced() {
    publish("multi-outputs-trigger:1:hash", REVIEW_PAYLOAD);
    waitForProcessedMessages(2);

    assertPublished("multi-output-1:1", null,
        "{\"multi-output-1\":[{\"stars\":1,\"text\":\"some review\"}]}");
    assertPublished("multi-output-2:1", null,
        "{\"multi-output-2\":[{\"stars\":1,\"text\":\"some review\"}]}");
  }

  @Test
  void shouldNotProcessMessageWithInvalidKey() {
    publish("multi-outputs-trigger:1", REVIEW_PAYLOAD);
    assertNoResultMessageWasSent();
  }

  private void assertReviewsPublished(int id, String expectedPayload) {
    assertPublished("reviews:" + id, "reviews/all", expectedPayload);
  }

}
