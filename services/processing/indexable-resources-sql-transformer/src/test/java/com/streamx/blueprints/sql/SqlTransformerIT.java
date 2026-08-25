package com.streamx.blueprints.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.sql.SqlTransformerIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class SqlTransformerIT extends BaseQuarkusIntegrationTest {

  private static final String RESOURCE_TYPE = "any";

  @Test
  void shouldEmitDataEvent() {
    // given
    String payload = """
        {"title":"test",
         "content":"content",
         "facets":{},
         "fields":{
            "url":"https://example.com",
            "description":"Description",
            "publication_date":"2025-07-15",
            "author":"David Beckham"
         }}
        """;
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(
        "/test.html",
        IndexableResource.TYPE_PUBLISHED,
        new IndexableResource(payload, RESOURCE_TYPE, Collections.emptyList()),
        CloudEventUtils.toOffsetDateTime(1)
    );

    // when
    sendEvent(sourceEvent, Channels.INDEXABLE_RESOURCES);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.DATA);
    assertOutgoingEvent(outgoingEvent);
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent) {
    assertThat(outgoingEvent).isNotNull();
    assertThat(outgoingEvent.getSubject()).isEqualTo("latestarticlesrss");
    assertThat(outgoingEvent.getType()).isEqualTo(Data.TYPE_PUBLISHED);
    var outgoingData = CloudEventUtils.getData(outgoingEvent, WebResource.class);
    assertThat(outgoingData).isNotNull();
    assertThat(outgoingData.getContentAsString()).isEqualTo(
        "{\"feeds\":[{\"subject\":\"latestarticlesrss\",\"title\":\"test\",\"url\":\"https://example.com\",\"description\":\"Description\",\"publicationDate\":\"2025-07-15\",\"modificationDate\":null,\"tags\":null,\"author\":\"David Beckham\",\"image\":null,\"language\":null,\"contentType\":null,\"metadata\":null}]}");
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      Map<String, String> properties = getConfigPropertiesFromYaml("application.yaml");
      properties.put("quarkus.datasource.jdbc.url", "jdbc:sqlite::memory:");
      properties.put("quarkus.otel.sdk.disabled", "true");
      return properties;
    }
  }
}
