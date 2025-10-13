package com.streamx.blueprints.web.server;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.web.server.storage.FileSystemResourceStorage;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    await().until(() -> canAccessViaHttp(expectedPath, publishedContent));
  }


  @ParameterizedTest
  @MethodSource("keyToExpectedPath")
  void shouldNotAccessUnpublishedPage(String subject, String expectedPath) {
    // given
    String publishedContent = publish(subject, Page::new);
    await().until(() -> canAccessViaHttp(expectedPath, publishedContent));

    // when
    unpublish(subject);

    // then
    await().until(() -> cannotAccessViaHttp(expectedPath));
  }

  @Test
  void shouldNotAccessUnsupportedType() {
    // given
    publish("unsupported.ext", new Object());

    // when
    makeSurePublicationProcessed();

    // then
    await().until(() -> cannotAccessViaHttp("/unsupported.ext"));
  }

  @Test
  void shouldNotUnpublishWholeDirectory() {
    // given
    String file = "/directory/file.html";
    String directory = "/directory";

    String publishedContent = publish(file, Page::new);
    await().until(() -> canAccessViaHttp(file, publishedContent));

    // when
    unpublish(directory);
    makeSurePublicationProcessed();

    // then
    await().until(() -> canAccessViaHttp(file, publishedContent));
  }

  @Test
  void shouldAppendIndexHtmlWhenStoringPageThatHasPathEndingWithSlash() {
    // given
    String subject = "blogs/pages/";
    String expectedPath = getExpectedDefaultNamespace() + "/blogs/pages/index.html";
    String expectedContent = "Some content";

    // when
    publishPage(subject, new Page(expectedContent));

    // then
    await().until(() -> canAccessViaHttp(expectedPath, expectedContent));

    // when
    unpublishPage(subject);

    // then
    await().until(() -> cannotAccessViaHttp(expectedPath));
  }

  @Test
  void shouldRemoveTrailingSlashWhenStoringResourceThatHasPathEndingWithSlash() {
    // given
    String subject = "images/image.jpg/";
    String expectedPath = getExpectedDefaultNamespace() + "/images/image.jpg";
    String assetContent = "Asset content";

    // when
    publish(subject, content -> new Asset(assetContent.getBytes(UTF_8)));

    // then
    await().until(() -> canAccessViaHttp(expectedPath, assetContent));

    // when
    unpublish(subject);

    // then
    await().until(() -> cannotAccessViaHttp(expectedPath));
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
    await().until(() -> canAccessViaHttp(synchronisationFile, syncContent));
  }

  private <T> String publish(String subject, Function<String, T> createPayloadFn) {
    String content = TEST_CONTENT.formatted(subject);
    T payload = createPayloadFn.apply(content);
    publish(subject, payload);
    return content;
  }

  private <T> void publish(String subject, T payload) {
    sendEvent(subject, payload, "com.streamx.blueprints.any.published.v1");
  }

  private <T> void publishPage(String subject, T payload) {
    sendEvent(subject, payload, Page.TYPE_PUBLISHED);
  }

  private <T> void unpublishPage(String subject) {
    sendEvent(subject, null, Page.TYPE_UNPUBLISHED);
  }

  private void unpublish(String subject) {
    sendEvent(subject, null, "com.streamx.blueprints.any.unpublished.v1");
  }

  private <T> void sendEvent(String subject, T payload, String type) {
    InMemorySource<CloudEvent> pages = connector.source(WebServerSink.CHANNEL);
    OffsetDateTime eventTime = OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(EVENT_TIME.getAndIncrement()), ZoneOffset.UTC);
    CloudEvent event = payload == null
        ? CloudEventUtils.eventWithoutData(subject, type, eventTime)
        : CloudEventUtils.eventWithData(subject, type, payload, eventTime);
    pages.send(event);
  }

  private boolean canAccessViaHttp(String path, String content) {
    try {
      given().basePath("/")
          .when()
          .get(path)
          .then()
          .statusCode(200)
          .body(containsString(content));
    } catch (AssertionError err) {
      return false;
    }
    return true;
  }

  private boolean cannotAccessViaHttp(String path) {
    try {
      given().basePath("/")
          .when()
          .get(path)
          .then()
          .statusCode(404);
    } catch (AssertionError err) {
      return false;
    }
    return true;
  }

}
