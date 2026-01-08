package com.streamx.blueprints.rewriter;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.OptimizedAsset;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.rewriter.LocalReferencesRewriterIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class LocalReferencesRewriterIT extends BaseQuarkusIntegrationTest {

  private static final File TEST_IMAGE_FILE = new File("src/test/resources/logo-optimized.webp");
  private static final String ORIGINAL_ASSET_KEY = "/images/logo.gif";
  private static final String OPTIMIZED_ASSET_KEY = "/images/logo-optimized.webp";

  private static final String PAGE_CONTENT_TEMPLATE = """
      <html>
       <head></head>
       <body>
        <img src="%s">
       </body>
      </html>""";

  private static final String PAGE_WITH_ORIGINAL_CONTENT = PAGE_CONTENT_TEMPLATE
      .formatted(ORIGINAL_ASSET_KEY);

  private static final String PAGE_WITH_OPTIMIZED_IMAGE = PAGE_CONTENT_TEMPLATE
      .formatted(OPTIMIZED_ASSET_KEY);

  private static final String PAGE_KEY = "/pages/index.html";
  private static final String PAGE_TYPE = "test-page";
  private static final Page PAGE = new Page(PAGE_WITH_ORIGINAL_CONTENT, PAGE_TYPE);

  @Test
  void shouldRewriteLocalReferences() throws IOException {
    // given: publish page referencing the unoptimized image
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(PAGE_KEY, Page.TYPE_PUBLISHED, PAGE);

    // when: publish the page while the optimized version of its image is available
    publishOptimizedImage();
    sendEvent(sourceEvent, Channels.INCOMING_PAGES);

    // then: assert page has a rewritten link
    CloudEvent outgoingEvent1 = waitForResponseEvent(Channels.ADJUSTED_PAGES);
    assertPageEventWithOptimizedImage(outgoingEvent1, sourceEvent);

    // when: publish the page while the optimized version of its image is not available
    unpublishOptimizedImage();
    sendEvent(sourceEvent, Channels.INCOMING_PAGES);

    // then: assert page has the original link
    CloudEvent outgoingEvent2 = waitForLastResponseEvent(Channels.ADJUSTED_PAGES, 2);
    assertPageEventWithOriginalContent(outgoingEvent2, sourceEvent);
  }

  private static void publishOptimizedImage() throws IOException {
    byte[] testFileContent = FileUtils.readFileToByteArray(TEST_IMAGE_FILE);
    var optimizedAsset = new OptimizedAsset(testFileContent, "test-image", ORIGINAL_ASSET_KEY);

    CloudEvent event = CloudEventUtils.eventWithData(
        OPTIMIZED_ASSET_KEY,
        OptimizedAsset.TYPE_PUBLISHED,
        optimizedAsset
    );
    sendOptimizedImageEvent(event);
  }

  private static void unpublishOptimizedImage() {
    CloudEvent event = CloudEventUtils.eventWithoutData(
        OPTIMIZED_ASSET_KEY,
        OptimizedAsset.TYPE_UNPUBLISHED
    );
    sendOptimizedImageEvent(event);
  }

  private static void sendOptimizedImageEvent(CloudEvent event) {
    sendStatefulEvent(event, Channels.OPTIMIZED_ASSETS, Channels.OPTIMIZED_ASSETS_STATE);
  }

  private static void assertPageEventWithOptimizedImage(CloudEvent outgoingEvent,
      CloudEvent sourceEvent) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).hasPath("local-references-rewriter");
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertPagePayload(outgoingEvent, PAGE_WITH_OPTIMIZED_IMAGE);
  }

  private static void assertPageEventWithOriginalContent(CloudEvent outgoingEvent,
      CloudEvent sourceEvent) {
    assertThat(outgoingEvent.getId()).isEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).hasPath(sourceEvent.getSource().getPath());
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertPagePayload(outgoingEvent, PAGE_WITH_ORIGINAL_CONTENT);
  }

  private static void assertPagePayload(CloudEvent event, String expectedPageContent) {
    assertThat(event.getType()).isEqualTo(Page.TYPE_PUBLISHED);

    Page page = CloudEventUtils.getData(event, Page.class);
    assertThat(page).isNotNull();
    assertThat(page.getType()).isEqualTo(PAGE_TYPE);
    assertThat(page.getContentAsString()).isEqualTo(expectedPageContent);
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      return Map.of(
          "streamx.blueprints.local-references-rewriter.processed-page-path-pattern", ".*"
      );
    }
  }
}