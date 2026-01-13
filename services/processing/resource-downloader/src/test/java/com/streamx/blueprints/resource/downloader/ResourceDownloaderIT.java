package com.streamx.blueprints.resource.downloader;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(BaseQuarkusIntegrationTestProfile.class)
public class ResourceDownloaderIT extends BaseQuarkusIntegrationTest {

  private static final String TEST_PAGE_PATH = "/pages/index.html";
  private static final String TEST_PAGE_DOWNLOAD_PATH = "/downloaded-pages/index.html";
  private static final String TEST_PAGE_CONTENT = "<html><body>Hello World</body></html>";

  private static final String EMITTED_PAGE_TYPE = "pages/external";
  private static final String EMITTED_WEB_RESOURCE_TYPE = "web-resources/external";
  private static final String EMITTED_ASSET_TYPE = "assets/external";

  @Test
  void shouldDownloadResource() {
    // given
    configureServiceToDownloadTestPageFromWiremock();

    String testPageUrl = "http://%s:%d%s".formatted(
        getContainerLocalhost(), getWiremockPort(), TEST_PAGE_PATH);

    DownloadRequest downloadRequest = new DownloadRequest(
        testPageUrl,
        TEST_PAGE_DOWNLOAD_PATH,
        EMITTED_PAGE_TYPE,
        EMITTED_WEB_RESOURCE_TYPE,
        EMITTED_ASSET_TYPE
    );
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(
        "any", // note: subject is not relevant for DownloadRequests
        DownloadRequest.DOWNLOAD_EVENT_TYPE, downloadRequest);

    // when
    sendEvent(sourceEvent, Channels.DOWNLOAD_REQUESTS);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.DOWNLOADED_PAGES);
    assertOutgoingEvent(outgoingEvent, sourceEvent, downloadRequest, TEST_PAGE_CONTENT);
  }

  private void configureServiceToDownloadTestPageFromWiremock() {
    wiremock.register(head(urlEqualTo(TEST_PAGE_PATH))
        .willReturn(aResponse()
            .withStatus(200)
        ));

    wiremock.register(get(urlEqualTo(TEST_PAGE_PATH))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/html")
            .withBody(TEST_PAGE_CONTENT)
        ));
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      DownloadRequest downloadRequest, String sourcePageContent) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("resource-downloader");
    assertThat(outgoingEvent.getSubject()).isEqualTo(downloadRequest.emitKey());
    assertThat(outgoingEvent.getType()).isEqualTo(Page.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isNotEqualTo(sourceEvent.getTime());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, Page.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(EMITTED_PAGE_TYPE);
    assertThat(outgoingResource.getContentAsString()).isEqualTo(sourcePageContent);
  }

}