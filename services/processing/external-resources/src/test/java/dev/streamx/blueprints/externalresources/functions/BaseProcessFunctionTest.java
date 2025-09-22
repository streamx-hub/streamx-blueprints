package dev.streamx.blueprints.externalresources.functions;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.streamx.blueprints.data.Asset;
import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.services.ExternalResourcesCollector;
import dev.streamx.blueprints.externalresources.testutils.SkipVerifyingNoDownloadErrors;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import dev.streamx.quasar.reactive.messaging.utils.MetadataUtils;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

abstract class BaseProcessFunctionTest extends BaseFunctionTest {

  @Inject
  Logger log;

  @InjectSpy
  ExternalResourcesProcessFunction externalResourcesProcessFunction;

  protected InMemorySource<Message<Page>> pagesChannel;
  protected InMemorySource<Message<WebResource>> webResourcesChannel;
  protected InMemorySource<Message<Data>> dataChannel;

  protected InMemorySink<Page> pagesSink;
  protected InMemorySink<WebResource> webResourcesSink;
  protected InMemorySink<Data> dataSink;
  protected InMemorySink<Asset> assetsSink;

  @BeforeEach
  void initSourcesAndSinks() {
    pagesChannel = getSource(Channels.INCOMING_PAGES);
    webResourcesChannel = getSource(Channels.INCOMING_WEB_RESOURCES);
    dataChannel = getSource(Channels.INCOMING_DATA);

    pagesSink = getSink(Channels.OUTGOING_PAGES);
    webResourcesSink = getSink(Channels.OUTGOING_WEB_RESOURCES);
    dataSink = getSink(Channels.OUTGOING_DATA);
    assetsSink = getSink(Channels.OUTGOING_ASSETS);
  }

  @AfterEach
  void verifyNoDownloadErrors(TestInfo testInfo) {
    if (!testInfo.getTestMethod().orElseThrow()
        .isAnnotationPresent(SkipVerifyingNoDownloadErrors.class)) {
      verify(externalResourcesProcessFunction, never()).logDownloadingError(any(), any());
    }
  }

  protected <T extends Resource> Message<T> publish(InMemorySource<Message<T>> channel,
      T resource, String path, String sxType) {
    return sendToChannel(channel, resource, path, Action.PUBLISH, sxType);
  }

  protected <T extends Resource> Message<T> unpublish(InMemorySource<Message<T>> channel,
      String path, String sxType) {
    return sendToChannel(channel, null, path, Action.UNPUBLISH, sxType);
  }

  private static <T extends Resource> Message<T> sendToChannel(
      InMemorySource<Message<T>> channel, T resource,
      String path, Action action, String sxType) {
    Message<T> message = Message.of(
        resource,
        Metadata.of(
            Key.of(path),
            EventTime.of(System.currentTimeMillis()),
            action,
            Properties.empty().withType(sxType)
        )
    );
    channel.send(message);
    return message;
  }

  protected <T extends Resource> void waitForMessagesInSink(InMemorySink<T> sink,
      int expectedCount) {
    try {
      await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
          assertThat(sink.received()).hasSize(expectedCount)
      );
    } catch (ConditionTimeoutException ex) {
      dumpCurrentMessagesInSink(sink);
      throw ex;
    }
  }

  private <T extends Resource> void dumpCurrentMessagesInSink(InMemorySink<T> sink) {
    log.infof("Current messages in %s sink:", sink.name());
    List<? extends Message<T>> messages = sink.received();
    for (int i = 0; i < messages.size(); i++) {
      Message<T> message = messages.get(i);
      log.infof("Message #%d: %s", i, MetadataUtils.extractKey(message));
      if (message.getPayload() != null) {
        log.info(message.getPayload().getContentAsString());
      }
    }
  }

  protected void assertPublishedPage(int indexInSink, String expectedKey, String expectedContent) {
    Message<Page> message = pagesSink.received().get(indexInSink);
    verifyPublishedTextResource(message, expectedKey, expectedContent);
  }

  protected void assertPublishedWebResource(int indexInSink, String expectedKey,
      String expectedContent) {
    Message<WebResource> message = webResourcesSink.received().get(indexInSink);
    verifyPublishedTextResource(message, expectedKey, expectedContent);
  }

  protected void assertPublishedData(int indexInSink, String expectedKey, String expectedContent) {
    Message<Data> message = dataSink.received().get(indexInSink);
    verifyPublishedTextResource(message, expectedKey, expectedContent);
  }

  private static <T extends Resource> void verifyPublishedTextResource(Message<T> message,
      String expectedKey, String expectedContent) {
    assertThat(extractKey(message)).isEqualTo(expectedKey);
    assertThat(extractAction(message)).isSameAs(Action.PUBLISH);
    assertThat(message.getPayload().getContentAsString()).isEqualTo(expectedContent);
  }

  protected void assertPublishedAsset(int indexInSink, String expectedKey, byte[] expectedContent) {
    Message<Asset> message = assetsSink.received().get(indexInSink);
    verifyPublishedAsset(message, expectedKey, expectedContent);
  }

  private static void verifyPublishedAsset(Message<Asset> message, String expectedKey,
      byte[] expectedContent) {
    assertThat(extractKey(message)).isEqualTo(expectedKey);
    assertThat(extractAction(message)).isSameAs(Action.PUBLISH);
    assertThat(message.getPayload().getContent().array()).containsExactly(expectedContent);
  }

  protected void assertPublishedAssets(Map<String, byte[]> expectedAssets) {
    List<? extends Message<Asset>> sortedActualMessages = assetsSink.received().stream()
        .sorted(Comparator.comparing(MetadataUtils::extractKey))
        .toList();
    assertThat(sortedActualMessages).hasSize(expectedAssets.size());

    List<Map.Entry<String, byte[]>> sortedExpectedAssets = new ArrayList<>(
        new TreeMap<>(expectedAssets).entrySet());

    for (int i = 0; i < sortedActualMessages.size(); i++) {
      Message<Asset> message = sortedActualMessages.get(i);
      Entry<String, byte[]> asset = sortedExpectedAssets.get(i);
      verifyPublishedAsset(message, asset.getKey(), asset.getValue());
    }
  }

  protected static <T extends Resource> void assertSameMessages(Message<T> message1,
      Message<T> message2) {
    if (extractAction(message1).equals(Action.PUBLISH)) {
      assertThat(message1.getPayload().getContentAsString())
          .isEqualTo(message2.getPayload().getContentAsString());
    } else {
      assertThat(message1.getPayload()).isNull();
      assertThat(message2.getPayload()).isNull();
    }
    assertSameMetadata(message1.getMetadata(), message2.getMetadata());
  }

  private static void assertSameMetadata(Metadata relayed, Metadata original) {
    assertThat(relayed.get(Key.class)).isEqualTo(original.get(Key.class));
    assertThat(relayed.get(Action.class)).isEqualTo(original.get(Action.class));
    assertThat(relayed.get(EventTime.class)).isEqualTo(original.get(EventTime.class));
  }

  protected static <T extends Resource> void sendMessagesFromSinkToChannel(InMemorySink<T> sink,
      InMemorySource<Message<T>> channel) {
    sink.received().forEach(channel::send);
  }

  protected static <T extends Resource, F extends BaseProcessResourceFunction<T>>
      void overrideResourceSelectors(F functionSpy, String... selectors) {
    ExternalResourcesCollector collector = functionSpy.externalResourcesCollector();

    try {
      Field selectorsField = collector.getClass().getDeclaredField("resourceSelectors");
      selectorsField.setAccessible(true);
      selectorsField.set(collector, List.of(selectors));
    } catch (ReflectiveOperationException ex) {
      fail(ex);
    }

    doReturn(collector).when(functionSpy).externalResourcesCollector();
    functionSpy.init();
  }
}
