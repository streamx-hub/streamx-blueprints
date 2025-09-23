package dev.streamx.blueprints.externalresources.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.externalresources.Channels;
import dev.streamx.blueprints.externalresources.data.ExternalResource;
import dev.streamx.blueprints.externalresources.data.ParentResource;
import dev.streamx.blueprints.externalresources.testutils.UsesTestWebServer;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExternalResourcesProcessFunctionTest extends BaseFunctionTest
    implements UsesTestWebServer {

  private InMemorySink<CloudEvent> resourcesSink;

  @InjectSpy
  ExternalResourcesProcessFunction processFunction;

  @BeforeEach
  void initSink() {
    resourcesSink = getSink(Channels.OUTGOING_RESOURCES);
  }

  @Test
  void shouldSupportLastModifiedHeaders() {
    // given
    String imagePath = "/logo.gif";
    byte[] imageContent = new byte[]{0, 1, 2};
    String imageUrl = uploadImage(imagePath, imageContent);
    ExternalResource imageResource = new ExternalResource(imagePath, imageUrl, imagePath);

    ParentResource<Page> parentPage = new ParentResource<>(
        "http://server.com/blog.html", "/blog.html", "<img src='/logo.gif'>", "page/blog", Page.class);

    // when: publish page
    downloadAndPublish(imageResource, parentPage);

    // then
    assertSingleAssetInSink(imagePath);

    // and: publish the same page again
    downloadAndPublish(imageResource, parentPage);

    // then
    assertSingleAssetInSink(imagePath);

    verify(processFunction).tracef(
        "Success downloading %s (referenced by parent resource %s) as %s",
        imageUrl,
        parentPage.getStreamxKey(),
        imageResource.getStreamxKey()
    );
    verify(processFunction).tracef("Not downloading unchanged external resource %s",
        imageUrl
    );
  }

  private void assertSingleAssetInSink(String expectedKey) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
        assertThat(resourcesSink.received()).hasSize(1)
    );
    assertThat(resourcesSink.received().get(0).getPayload().getSubject()).isEqualTo(expectedKey);
  }

  private void downloadAndPublish(ExternalResource resource, ParentResource<Page> parentResource) {
    var response = processFunction.downloadAndPublish(resource, parentResource);
    response.await().indefinitely();
  }
}