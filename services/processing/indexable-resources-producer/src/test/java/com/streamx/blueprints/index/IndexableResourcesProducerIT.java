package com.streamx.blueprints.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.index.IndexableResourcesProducerIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class IndexableResourcesProducerIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldProduceIndexableResource() throws IOException {
    // given
    Page page = new Page("<b>Hello World</b>", "test-page");
    String key = "pages/test-page.html";
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(key, Page.TYPE_PUBLISHED, page);

    // when
    sendEvent(sourceEvent, Channels.INCOMING_PAGES);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.INDEXABLE_RESOURCES);
    assertOutgoingEvent(outgoingEvent, sourceEvent, page,
        IndexableResource.TYPE_PUBLISHED,
        """
            {"title":"pages/test-page.html","content":"<b>Hello World</b>"}"""
    );
  }

  @Test
  void shouldProduceIndexableResourceFragment() throws IOException {
    // given
    Fragment fragment = new Fragment("<b>Header</b>", "test-fragment");
    String key = "fragments/test-fragment.html";
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(key, Fragment.TYPE_PUBLISHED, fragment);

    // when
    sendEvent(sourceEvent, Channels.INCOMING_FRAGMENTS);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.INDEXABLE_RESOURCE_FRAGMENTS);
    assertOutgoingEvent(outgoingEvent, sourceEvent, fragment,
        IndexableResourceFragment.TYPE_PUBLISHED,
        """
            {"content":"<b>Header</b>"}"""
    );
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      WebResource sourceResource, String expectedEventType, String expectedOutgoingContent) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("indexable-resources-producer");
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getType()).isEqualTo(expectedEventType);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertThat(outgoingEvent.getDataContentType()).isEqualTo(sourceEvent.getDataContentType());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, IndexableResource.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(sourceResource.getType());
    assertThat(outgoingResource.getContentAsString())
        .isNotEqualTo(sourceResource.getContentAsString())
        .isEqualTo(expectedOutgoingContent);
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      return Map.of("streamx.blueprints.indexable-resources-producer.index-fragments", "true");
    }
  }
}