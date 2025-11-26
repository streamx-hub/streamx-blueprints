package com.streamx.blueprints.rewriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

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
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class LocalReferencesRewriterIT extends BaseQuarkusIntegrationTest {

  private static final File TEST_IMAGE_FILE = new File("src/test/resources/logo-optimized.webp");
  private static final String ORIGINAL_ASSET_KEY = "/images/logo.gif";
  private static final String OPTIMIZED_ASSET_KEY = "/images/logo-optimized.webp";

  @Test
  void shouldRewriteLocalReferences() {
    // given: prepare page referencing the unoptimized image
    String pageContent = """
        <html>
          <body>
            <img src='%s' />
          </body>
        </html>""".formatted(ORIGINAL_ASSET_KEY);
    String pageKey = "/pages/index.html";

    Page page = new Page(pageContent, "test-page");
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(pageKey, Page.TYPE_PUBLISHED, page);

    // when
    publishOptimizedImage();
    sendEvent(sourceEvent, Channels.INCOMING_PAGES);

    // then: assert page has a rewritten link
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.ADJUSTED_PAGES);
    assertOutgoingEvent(outgoingEvent, sourceEvent, page, """
        <html>
         <head></head>
         <body>
          <img src="%s">
         </body>
        </html>""".formatted(OPTIMIZED_ASSET_KEY)
    );
  }

  private static void publishOptimizedImage() {
    byte[] testFileContent = contentOf(TEST_IMAGE_FILE).getBytes();

    CloudEvent sourceAssetEvent = CloudEventUtils.eventWithData(
        OPTIMIZED_ASSET_KEY,
        OptimizedAsset.TYPE_PUBLISHED,
        new OptimizedAsset(testFileContent, "test-image", ORIGINAL_ASSET_KEY)
    );
    sendEvent(sourceAssetEvent, Channels.OPTIMIZED_ASSETS);
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      Page sourceData, String expectedOutgoingPageContent) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).hasPath("local-references-rewriter");
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, Page.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(sourceData.getType());
    assertThat(outgoingResource.getContentAsString()).isEqualTo(expectedOutgoingPageContent);
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