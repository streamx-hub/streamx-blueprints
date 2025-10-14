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
class ProcessDataFunctionTest extends ProcessDataFunctionBaseTest {

  private static final String PRODUCT_WITHOUT_PRICE = "{\"id\":\"%s\"}";
  private static final String PRODUCT_WITH_PRICE = "{\"id\":\"%s\",\"price\":\"%d\"}";
  private static final String PRICE_PAYLOAD = "{\"price\":\"%d\"}";
  private static final String REVIEWS_PAYLOAD = "{"
      + "\"stars\":\"5\","
      + "\"stringList\":[\"Great product\",\"Super product\",\"Very nice chair\"]"
      + "}";

  @Inject
  @Any
  InMemoryConnector connector;

  InMemorySource<CloudEvent> dataSource;

  InMemorySink<CloudEvent> dataSink;

  @Override
  protected InMemorySource<CloudEvent> getDataSource() {
    return dataSource;
  }

  @Override
  public InMemorySink<CloudEvent> getDataSink() {
    return dataSink;
  }

  @BeforeEach
  void beforeEach() {
    dataSource = connector.source(Channels.DATA);
    dataSink = connector.sink(Channels.AGGREGATED_DATA);
    dataSink.clear();
  }

  @Test
  void expectProductWithOnlyMasterDataIfPriceNotExist() {
    int id = 1;
    publishProductWithoutPrice(id);
    waitForProcessedMessages(1);
    assertProductPublishedWithoutPrice(id);
  }

  @Test
  void expectProductWithOnlyMasterDataIfPriceIsUnpublished() {
    int id = 2;
    publishProductWithoutPrice(id);
    publishPrice(id, 200);
    waitForProcessedMessages(2);
    assertProductPublishedWithPrice(id, 200);

    unpublishPrice(id);
    waitForProcessedMessages(3);
    assertProductPublishedWithoutPrice(id);
  }

  @Test
  void expectNoResourceWhenMasterMissing() {
    int id = 3;
    publishPrice(id, 200);
    assertNoResultMessageWasSent();

    unpublishPrice(id);
    assertNoResultMessageWasSent();
  }

  @Test
  void shouldMergePrice_IntoProduct_ThatWasPublishedWithoutPrice() {
    int id = 4;
    publishProductWithoutPrice(id);
    publishPrice(id, 200);
    waitForProcessedMessages(2);
    assertProductPublishedWithPrice(id, 200);
  }

  @Test
  void shouldMergeNewPrice_IntoProduct_ThatWasPublishedWithOldPrice() {
    int id = 5;
    publishProductHavingPrice(id, 100);
    publishPrice(id, 200);
    waitForProcessedMessages(2);
    assertProductPublishedWithPrice(id, 200);
  }

  @Test
  void expectMergedThreeResource() {
    int id = 6;
    publishProductWithoutPrice(id);
    publishPrice(id, 200);
    publishReview(id);
    waitForProcessedMessages(3);

    assertProductPublished(id,
        "{"
        + "\"id\":\"" + id + "\","
        + "\"price\":\"200\","
        + "\"stars\":\"5\","
        + "\"stringList\":[\"Great product\",\"Super product\",\"Very nice chair\"]"
        + "}"
    );
  }

  @Test
  void expectNoResourceWhenMasterUnpublished() {
    int id = 7;
    publishProductWithoutPrice(id);
    publishReview(id);
    waitForProcessedMessages(2);
    assertProductPublished(id,
        "{"
        + "\"id\":\"" + id + "\","
        + "\"stars\":\"5\","
        + "\"stringList\":[\"Great product\",\"Super product\",\"Very nice chair\"]"
        + "}"
    );

    unpublishProduct(id);
    waitForProcessedMessages(3);
    assertProductUnpublished(id);
  }

  @Test
  void expectPriceChangeIsRevertedWhenNewPriceIsUnpublished() {
    int id = 8;
    publishProductHavingPrice(id, 100);
    publishPrice(id, 200);
    waitForProcessedMessages(2);
    assertProductPublishedWithPrice(id, 200);

    unpublishPrice(id);
    waitForProcessedMessages(3);
    assertProductPublishedWithPrice(id, 100);
  }

  @Test
  void unpublishingPrice_ShouldNotUnpublishProduct() {
    int id = 9;
    publishProductWithoutPrice(id);
    waitForProcessedMessages(1);
    assertProductPublishedWithoutPrice(id);

    unpublishPrice(id);
    waitForProcessedMessages(2);
    assertProductPublishedWithoutPrice(id); // expect no changes

    publishPrice(id, 200);
    waitForProcessedMessages(3);
    assertProductPublishedWithPrice(id, 200);

    unpublishPrice(id);
    waitForProcessedMessages(4);
    assertProductPublishedWithoutPrice(id);
  }

  @Test
  void publishPrice_ShouldUpdatePriceOfProduct_ThatWasImportedFromOtherDataSource() {
    int id = 10;
    publish("other-pim:" + id, PRODUCT_WITH_PRICE.formatted(id, 100));

    publishPrice(id, 200);
    assertProductPublishedWithPrice(id, 200);
  }

  @Test
  void unpublishPriceWithoutPriorPublishPriceWillNotModifyProductPrice() {
    int id = 11;
    publishProductHavingPrice(id, 100);

    unpublishPrice(id);
    waitForProcessedMessages(2);
    assertProductPublishedWithPrice(id, 100);
  }

  @Test
  void expectMultipleOutputsToBeProduced() {
    publish("multi-outputs-trigger:1", PRODUCT_WITHOUT_PRICE.formatted(1));
    waitForProcessedMessages(2);

    assertPublished("multi-output-1:1", null, "{\"id\":\"1\"}");
    assertPublished("multi-output-2:1", null, "{\"id\":\"1\"}");
  }

  @Test
  void expectMasterTypeToBeSetOnOutputResource() {
    String masterResourceTestType = "master-resource-test-type";
    String price200Payload = PRICE_PAYLOAD.formatted(200);
    publish("test-type-optional:1", "optional-resource-test-type", price200Payload);
    publish("test-type-master:1", masterResourceTestType,
        PRODUCT_WITHOUT_PRICE.formatted(1));
    waitForProcessedMessages(1);

    assertPublished("test-type-output:1", masterResourceTestType,
        "{\"id\":\"1\",\"price\":\"200\"}");
  }

  @Test
  void shouldNotProcessMessageWithInvalidKey() {
    publish("pim", PRODUCT_WITHOUT_PRICE.formatted(1));
    assertNoResultMessageWasSent();
  }

  private void publishProductWithoutPrice(int id) {
    publish("pim:" + id, PRODUCT_WITHOUT_PRICE.formatted(id));
  }

  private void publishProductHavingPrice(int id, int price) {
    publish("pim:" + id, PRODUCT_WITH_PRICE.formatted(id, price));
  }

  private void publishPrice(int id, int price) {
    publish("price:" + id, PRICE_PAYLOAD.formatted(price));
  }

  private void publishReview(int id) {
    publish("reviews:" + id, REVIEWS_PAYLOAD);
  }

  private void unpublishProduct(int id) {
    unpublish("pim:" + id);
  }

  private void unpublishPrice(int id) {
    unpublish("price:" + id);
  }

  private void assertProductPublishedWithoutPrice(int id) {
    String expectedPayload = PRODUCT_WITHOUT_PRICE.formatted(id);
    assertProductPublished(id, expectedPayload);
  }

  private void assertProductPublishedWithPrice(int id, int price) {
    String expectedPayload = PRODUCT_WITH_PRICE.formatted(id, price);
    assertProductPublished(id, expectedPayload);
  }

  private void assertProductPublished(int id, String expectedPayload) {
    assertPublished("product:" + id, "product/variant", expectedPayload);
  }

  private void assertProductUnpublished(int id) {
    assertUnpublished("product:" + id);
  }

}
