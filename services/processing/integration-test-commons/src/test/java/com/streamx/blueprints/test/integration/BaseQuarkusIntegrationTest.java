package com.streamx.blueprints.test.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.reactive.messaging.http.CloudEventJsonDeserializer;
import com.streamx.reactive.messaging.http.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

@ConnectWireMock
public abstract class BaseQuarkusIntegrationTest {

  private static final CloudEventJsonSerializer eventsSerializer = new CloudEventJsonSerializer();
  private static final CloudEventJsonDeserializer eventsDeserializer
      = new CloudEventJsonDeserializer();

  // will be injected automatically when the test class is annotated with @ConnectWireMock
  protected WireMock wiremock;

  @BeforeAll
  static void setEventSource() {
    System.setProperty("quarkus.application.name",
        BaseQuarkusIntegrationTest.class.getSimpleName());
  }

  @BeforeEach
  void setupEndpointsForReceivingOutgoingEvents() {
    for (String channel : ChannelsReader.OUTGOING_CHANNELS) {
      String endpoint = toEndpoint(channel);
      wiremock.register(post(urlEqualTo(endpoint))
          .willReturn(aResponse().withStatus(202)));
    }
  }

  protected static void sendEvent(CloudEvent cloudEvent, String channel) throws IOException {
    String serializedEvent = eventsSerializer.serialize(cloudEvent).toString();
    String url = toUrl(channel);

    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpPost post = new HttpPost(url);
      post.setEntity(new StringEntity(serializedEvent));
      CloseableHttpResponse response = http.execute(post);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }
  }

  protected static <T> void sendEvent(String key, String eventType, T data, String channel)
      throws IOException {
    CloudEvent event = CloudEventUtils.eventWithData(key, eventType, data);
    sendEvent(event, channel);
  }

  /**
   * Some services are configured in the mesh file to use the same ref for outgoing and incoming
   * channels. Since QuarkusIntegrationTests don't use a mesh file, this method can be used to
   * manually simulate that behavior
   */
  protected void sendFromOutgoingToIncomingChannel(String outgoing, String incoming)
      throws IOException {
    CloudEvent outgoingEvent = waitForResponseEvent(outgoing);
    sendEvent(outgoingEvent, incoming);
  }

  protected CloudEvent waitForResponseEvent(String outgoingChannel) {
    return waitForLastResponseEvent(outgoingChannel, 1);
  }

  protected CloudEvent waitForLastResponseEvent(String outgoingChannel, int totalCount) {
    return waitForResponseEvents(outgoingChannel, totalCount).getLast();
  }

  private List<CloudEvent> waitForResponseEvents(String outgoingChannel, int totalCount) {
    String endpoint = toEndpoint(outgoingChannel);
    List<LoggedRequest> responses = waitForResponseRequests(endpoint, totalCount);
    return responses.stream()
        .map(LoggedRequest::getBody)
        .map(body -> eventsDeserializer.deserialize(Buffer.buffer(body)))
        .toList();
  }

  private static List<LoggedRequest> waitForResponseRequests(String endpoint, int totalCount) {
    List<LoggedRequest> results = new LinkedList<>();
    await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
      List<LoggedRequest> requests = WireMock.findAll(postRequestedFor(urlEqualTo(endpoint)));
      assertThat(requests).hasSize(totalCount);
      results.addAll(requests);
    });
    return results;
  }

  static Map<String, String> propertiesForOutgoingChannels() {
    Map<String, String> properties = new HashMap<>();

    String host = getContainerLocalhost();
    for (String channel : ChannelsReader.OUTGOING_CHANNELS) {
      String endpoint = toEndpoint(channel);
      properties.put(
          "mp.messaging.outgoing." + channel + ".url",
          "http://%s:${quarkus.wiremock.devservices.port}%s".formatted(host, endpoint)
      );
    }

    return properties;
  }

  private static String toUrl(String channel) {
    return "http://localhost:8081/" + channel;
  }

  private static String toEndpoint(String channel) {
    return "/" + channel;
  }

  protected static String getContainerLocalhost() {
    if (System.getProperty("os.name", "").toLowerCase().startsWith("linux")) {
      return "172.17.0.1";
    } else {
      return "host.docker.internal";
    }
  }

  protected static int getWiremockPort() {
    return ConfigProvider.getConfig()
        .getValue("quarkus.wiremock.devservices.port", Integer.class);
  }
}