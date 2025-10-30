package com.streamx.blueprints.web.server.sink;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.web.server.Channels;
import com.streamx.blueprints.web.server.storage.FileSystemResourceStorage;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public abstract class WebResourcesAccessTestBase {

  private static final String TEST_CONTENT = "test content for %s";
  private static final AtomicLong EVENT_TIME = new AtomicLong(1);

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  WebServerSink webServerSink;

  @InjectSpy
  FileSystemResourceStorage fileSystemResourceStorage;

  @ParameterizedTest
  @MethodSource("keyToExpectedPath")
  void shouldAccessPublishedPage(String subject, String expectedPath) {
    // when
    String publishedContent = publish(subject, Page::new);

    // then
    assertCanAccessViaHttp(expectedPath, publishedContent);
  }


  @ParameterizedTest
  @MethodSource("keyToExpectedPath")
  void shouldNotAccessUnpublishedPage(String subject, String expectedPath) {
    // given
    String publishedContent = publish(subject, Page::new);
    assertCanAccessViaHttp(expectedPath, publishedContent);

    // when
    unpublish(subject);

    // then
    assertCannotAccessViaHttp(expectedPath);
  }

  @Test
  void shouldNotAccessUnsupportedType() {
    // given
    publish("unsupported.ext", new Object());

    // when
    makeSurePublicationProcessed();

    // then
    assertCannotAccessViaHttp("/unsupported.ext");
  }

  @Test
  void shouldNotUnpublishWholeDirectory() {
    // given
    String file = "/directory/file.html";
    String directory = "/directory";

    String publishedContent = publish(file, Page::new);
    assertCanAccessViaHttp(file, publishedContent);

    // when
    unpublish(directory);
    makeSurePublicationProcessed();

    // then
    assertCanAccessViaHttp(file, publishedContent);
  }

  @Test
  void shouldAppendIndexHtmlWhenStoringPageThatHasPathEndingWithSlash() {
    verifyStoragePath(
        "blogs/pages/",
        "/blogs/pages/index.html",
        new Page("Some content"),
        Page.TYPE_PUBLISHED,
        Page.TYPE_UNPUBLISHED
    );
  }

  @Test
  void shouldAppendIndexHtmlWhenStoringFragmentThatHasPathEndingWithSlash() {
    verifyStoragePath(
        "blogs/fragments/",
        "/blogs/fragments/index.html",
        new Fragment("Some content"),
        Fragment.TYPE_PUBLISHED,
        Fragment.TYPE_UNPUBLISHED
    );
  }

  @Test
  void shouldNotAppendIndexHtmlWhenStoringWebResourceThatHasPathEndingWithSlash() {
    verifyStoragePath(
        "blogs/web-resources/",
        "/blogs/web-resources",
        new WebResource("Some content"),
        WebResource.TYPE_PUBLISHED,
        WebResource.TYPE_UNPUBLISHED
    );
  }

  @Test
  void shouldNotAppendIndexHtmlWhenStoringAssetThatHasPathEndingWithSlash() {
    verifyStoragePath(
        "blogs/assets/",
        "/blogs/assets",
        new Asset(new byte[]{0, 1, 2}),
        Asset.TYPE_PUBLISHED,
        Asset.TYPE_UNPUBLISHED
    );
  }

  private <T extends Resource> void verifyStoragePath(
      String subject, String expectedPath, T resource,
      String publishEventType, String unpublishEventType
  ) {
    // given
    String expectedFullPath = getExpectedDefaultNamespace() + expectedPath;

    // when
    publish(subject, resource, publishEventType);

    // then
    assertCanAccessViaHttp(expectedFullPath, resource.getContentAsString());

    // when
    unpublish(subject, unpublishEventType);

    // then
    assertCannotAccessViaHttp(expectedPath);
  }

  @Test
  void shouldRemoveTrailingSlashWhenStoringResourceThatHasPathEndingWithSlash() {
    // given
    String subject = "images/image.jpg/";
    String expectedPath = getExpectedDefaultNamespace() + "/images/image.jpg";
    String assetContent = "Asset content";

    // when
    publish(subject, content -> new Asset(assetContent.getBytes()));

    // then
    assertCanAccessViaHttp(expectedPath, assetContent);

    // when
    unpublish(subject);

    // then
    assertCannotAccessViaHttp(expectedPath);
  }


  @Test
  void shouldSkipProcessingMessageWithUnexpectedPayload() {
    // given
    String filePath = "/directory/file.html";
    Object payload = new Object();
    CloudEvent cloudEvent = CloudEventUtils.eventWithData(filePath, "unexpected", payload);

    // when
    webServerSink.consume(cloudEvent);

    // then
    verify(fileSystemResourceStorage, never()).add(any(), any());
    verify(fileSystemResourceStorage, never()).delete(any());
  }

  protected abstract String getExpectedDefaultNamespace();

  private Stream<Arguments> keyToExpectedPath() {
    return Stream.of(
        Arguments.of("test1.extension", getExpectedDefaultNamespace() + "/test1.extension"),
        Arguments.of("/test2.extension", getExpectedDefaultNamespace() + "/test2.extension"),
        Arguments.of("parent1/test3.extension",
            getExpectedDefaultNamespace() + "/parent1/test3.extension"),
        Arguments.of("/parent2/test4.extension",
            getExpectedDefaultNamespace() + "/parent2/test4.extension"),
        Arguments.of("parent3/sub-parent1/test5.extension",
            getExpectedDefaultNamespace() + "/parent3/sub-parent1/test5.extension"),
        Arguments.of("ns1:test1.extension", "ns1/test1.extension"),
        Arguments.of("ns2/ns3:test1.extension", "ns2/ns3/test1.extension")
    );
  }

  // publication of other file assures all previous publications/unpublications has been procesed
  private void makeSurePublicationProcessed() {
    String synchronisationFile = "/sync.txt";
    String syncContent = publish(synchronisationFile, Page::new);
    assertCanAccessViaHttp(synchronisationFile, syncContent);
  }

  private <T> String publish(String subject, Function<String, T> createPayloadFn) {
    String content = TEST_CONTENT.formatted(subject);
    T payload = createPayloadFn.apply(content);
    publish(subject, payload);
    return content;
  }

  private <T> void publish(String subject, T payload) {
    publish(subject, payload, "com.streamx.blueprints.any.published.v1");
  }

  private <T> void publish(String subject, T payload, String eventType) {
    sendEvent(subject, payload, eventType);
  }

  private <T> void publishPage(String subject, T payload) {
    sendEvent(subject, payload, Page.TYPE_PUBLISHED);
  }

  private <T> void unpublishPage(String subject) {
    sendEvent(subject, null, Page.TYPE_UNPUBLISHED);
  }

  private void unpublish(String subject) {
    unpublish(subject, "com.streamx.blueprints.any.unpublished.v1");
  }

  private void unpublish(String subject, String eventType) {
    sendEvent(subject, null, eventType);
  }

  private <T> void sendEvent(String subject, T payload, String type) {
    InMemorySource<CloudEvent> pages = connector.source(Channels.RESOURCES);
    OffsetDateTime eventTime = CloudEventUtils.toOffsetDateTime(EVENT_TIME.getAndIncrement());
    CloudEvent event = payload == null
        ? CloudEventUtils.eventWithoutData(subject, type, eventTime)
        : CloudEventUtils.eventWithData(subject, type, payload, eventTime);
    pages.send(event);
  }

  private static void assertCanAccessViaHttp(String path, String content) {
    await().untilAsserted(() ->
        given().basePath("/")
            .when()
            .get(path)
            .then()
            .statusCode(200)
            .body(containsString(content)));
  }

  private static void assertCannotAccessViaHttp(String path) {
    await().untilAsserted(() ->
        given().basePath("/")
            .when()
            .get(path)
            .then()
            .statusCode(404)
    );
  }

}
