package com.streamx.blueprints.event.converter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.event.converter.EventConverterIT.WireMockProfile;
import com.streamx.reactive.messaging.http.CloudEventJsonDeserializer;
import com.streamx.reactive.messaging.http.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@ConnectWireMock
@TestProfile(WireMockProfile.class)
public class EventConverterIT {

  private static final String INDEXABLE_RESOURCES_ENDPOINT = "/" + Channels.INDEXABLE_RESOURCES;

  // will be injected automatically when the test class is annotated with @ConnectWireMock
  WireMock wiremock;

  @BeforeAll
  static void setEventSource() {
    System.setProperty("quarkus.application.name", EventConverterIT.class.getSimpleName());
  }

  @BeforeEach
  void setupEndpointForReceivingOutgoingEvents() {
    wiremock.register(post(urlEqualTo(INDEXABLE_RESOURCES_ENDPOINT))
        .willReturn(aResponse().withStatus(202)));
  }

  @Test
  void shouldConvertDataToIndexableResource() throws IOException {
    Data data = new Data("{\"key\": \"value\"}", "type");
    CloudEvent sourceEvent = createPublishDataEvent(data);
    String serializedEvent = new CloudEventJsonSerializer().serialize(sourceEvent).toString();

    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpPost post = new HttpPost("http://localhost:8081/resources");
      post.setEntity(new StringEntity(serializedEvent));
      CloseableHttpResponse response = http.execute(post);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }

    LoggedRequest response = waitForResponseRequest();
    byte[] body = response.getBody();
    CloudEvent outgoingEvent = new CloudEventJsonDeserializer().deserialize(Buffer.buffer(body));
    assertOutgoingEvent(outgoingEvent, sourceEvent, data);
  }

  private static CloudEvent createPublishDataEvent(Data data) {
    return CloudEventUtils.eventWithData("key", Data.TYPE_PUBLISHED, data);
  }

  private static LoggedRequest waitForResponseRequest() {
    AtomicReference<LoggedRequest> result = new AtomicReference<>();
    await().untilAsserted(() -> {
      List<LoggedRequest> requests = WireMock.findAll(
          postRequestedFor(urlEqualTo(INDEXABLE_RESOURCES_ENDPOINT)));
      assertThat(requests).hasSize(1);
      result.set(requests.getFirst());
    });
    return result.get();
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      Data data) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("event-converter");
    assertThat(outgoingEvent.getSubject()).isEqualTo(sourceEvent.getSubject());
    assertThat(outgoingEvent.getType()).isEqualTo(IndexableResource.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertThat(outgoingEvent.getDataContentType()).isEqualTo(sourceEvent.getDataContentType());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, IndexableResource.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(data.getType());
    assertThat(outgoingResource.getContent()).isEqualTo(data.getContent());
  }

  public static class WireMockProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "mp.messaging.outgoing." + Channels.INDEXABLE_RESOURCES + ".url",
          "http://" + getContainerLocalhost() + ":${quarkus.wiremock.devservices.port}"
          + INDEXABLE_RESOURCES_ENDPOINT
      );
    }
  }

  private static String getContainerLocalhost() {
    if (System.getProperty("os.name", "").toLowerCase().startsWith("linux")) {
      return "172.17.0.1";
    } else {
      return "host.docker.internal";
    }
  }
}