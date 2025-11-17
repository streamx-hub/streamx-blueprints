package com.streamx.blueprints.event.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.event.converter.EventConverterIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class EventConverterIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldConvertDataToIndexableResource() throws IOException {
    // given
    Data data = new Data("{\"key\": \"value\"}", "type");
    CloudEvent sourceEvent = CloudEventUtils.eventWithData("key", Data.TYPE_PUBLISHED, data);

    // when
    sendEvent(sourceEvent, Channels.RESOURCES);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.INDEXABLE_RESOURCES);
    assertOutgoingEvent(outgoingEvent, sourceEvent, data);
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      Data sourceData) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("event-converter");
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getType()).isEqualTo(IndexableResource.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertThat(outgoingEvent.getDataContentType()).isEqualTo(sourceEvent.getDataContentType());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, IndexableResource.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(sourceData.getType());
    assertThat(outgoingResource.getContent()).isEqualTo(sourceData.getContent());
  }

  public static class IntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return propertiesForOutgoingChannels().build();
    }
  }
}