package com.streamx.blueprints.rewriter;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.rewriter.ExternalReferencesRewriterIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class ExternalReferencesRewriterIT extends BaseQuarkusIntegrationTest {

  private static final String SOURCE_PAGE_TYPE = "pages/sample-page";
  private static final String EXTERNAL_PAGE_TYPE = "pages/external";
  private static final String EXTERNAL_WEB_RESOURCE_TYPE = "web-resources/external";
  private static final String EXTERNAL_ASSET_TYPE = "assets/external";

  @Test
  void shouldRewriteExternalReferences() {
    // given
    String pageContent = """
        <html>
          <body>
            <img src='https://www.google.com/logo.jpg' />
          </body>
        </html>""";
    String key = "/pages/contact.html";

    Page page = new Page(pageContent, SOURCE_PAGE_TYPE);
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(key, Page.TYPE_PUBLISHED, page);

    // when
    sendEvent(sourceEvent, Channels.INCOMING_RESOURCES);

    // then: assert page has a rewritten link
    CloudEvent outgoingPageEvent = waitForResponseEvent(Channels.OUTGOING_RESOURCES);
    assertOutgoingPageEvent(outgoingPageEvent, sourceEvent, page);

    // and: download request should be sent
    CloudEvent outgoingDownloadRequestEvent = waitForResponseEvent(Channels.DOWNLOAD_REQUESTS);
    assertOutgoingDownloadRequestEvent(outgoingDownloadRequestEvent, sourceEvent);
  }

  private static void assertOutgoingPageEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      Page sourceData) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).hasPath("external-references-rewriter");
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, Page.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(sourceData.getType());
    assertThat(outgoingResource.getContentAsString()).isEqualTo("""
        <html>
          <body>
            <img src='/https_www.google.com/logo.jpg' />
          </body>
        </html>""");
  }

  private static void assertOutgoingDownloadRequestEvent(CloudEvent outgoingEvent,
      CloudEvent sourceEvent) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).hasPath("external-references-rewriter");
    assertThat(outgoingEvent.getSubject()).isEqualTo("/https_www.google.com/logo.jpg");
    assertThat(outgoingEvent.getType()).isEqualTo(DownloadRequest.DOWNLOAD_EVENT_TYPE);
    assertThat(outgoingEvent.getTime()).isNotEqualTo(sourceEvent.getTime());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, DownloadRequest.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.url()).isEqualTo("https://www.google.com/logo.jpg");
    assertThat(outgoingResource.emitKey()).isEqualTo("/https_www.google.com/logo.jpg");
    assertThat(outgoingResource.emittedPageType()).isEqualTo(EXTERNAL_PAGE_TYPE);
    assertThat(outgoingResource.emittedWebResourceType()).isEqualTo(EXTERNAL_WEB_RESOURCE_TYPE);
    assertThat(outgoingResource.emittedAssetType()).isEqualTo(EXTERNAL_ASSET_TYPE);
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      String basePropertyPath = "streamx.blueprints.external-references-rewriter.";
      return Map.of(
          basePropertyPath + "html-external-resource-xpath-selectors", "//img/@src",
          basePropertyPath + "base-url-for-relative-paths", "https://www.streamx.dev",
          basePropertyPath + "processable-payload-types", SOURCE_PAGE_TYPE,
          basePropertyPath + "emitted-page-type", EXTERNAL_PAGE_TYPE,
          basePropertyPath + "emitted-web-resource-type", EXTERNAL_WEB_RESOURCE_TYPE,
          basePropertyPath + "emitted-asset-type", EXTERNAL_ASSET_TYPE
      );
    }
  }
}