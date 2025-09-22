package dev.streamx.blueprints.externalresources.functions;

import dev.streamx.blueprints.data.WebResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;

@QuarkusTest
class ProcessHtmlWebResourceFunctionTest extends BaseProcessFunctionTest {

  @Test
  void shouldProcessPage() {
    // given
    String pagePath = "/page1.html";
    String pageContent = "<img src='./logo.png'>";

    mockDownloadResponse("https://www.my-eds-server.com/logo.png", new byte[]{0, 1, 2});

    // when
    publishWebResource(pagePath, pageContent);

    // then
    waitForMessagesInSink(assetsSink, 1);
    assertPublishedAsset(0,
        "/logo.png",
        new byte[]{0, 1, 2});

    waitForMessagesInSink(webResourcesSink, 1);
    assertPublishedWebResource(0,
        pagePath,
        "<img src='/logo.png'>");
  }

  @ParameterizedTest
  @EmptySource
  @NullSource
  @CsvSource("data/products")
  void shouldRelayResourceThatHasNotMatchingSxType(String sxType) {
    // given
    String path = "page.html";
    String content = "Hello World";

    // when
    Message<WebResource> publishMessage = publish(webResourcesChannel, new WebResource(content),
        path, sxType);

    // then
    waitForMessagesInSink(webResourcesSink, 1);

    // assert message is unchanged
    Message<WebResource> relayedMessage = webResourcesSink.received().get(0);
    assertSameMessages(relayedMessage, publishMessage);
  }

  @Test
  void shouldRelayResourceThatDoesNotHaveHtmlExtension() {
    // given
    String path = "/foo.txt";
    String content = "bar";

    // when
    Message<WebResource> publishMessage = publishWebResource(path, content);

    // then
    waitForMessagesInSink(webResourcesSink, 1);

    // assert message is unchanged
    Message<WebResource> relayedMessage = webResourcesSink.received().get(0);
    assertSameMessages(relayedMessage, publishMessage);
  }

  private Message<WebResource> publishWebResource(String path, String content) {
    return publish(webResourcesChannel, new WebResource(content), path, "web-resource/static");
  }
}
