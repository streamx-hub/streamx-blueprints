package com.streamx.blueprints.test.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.streamx.reactive.messaging.http.CloudEventJsonDeserializer;
import com.streamx.reactive.messaging.http.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.vertx.core.buffer.Buffer;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
    String serializedEvent = new CloudEventJsonSerializer().serialize(cloudEvent).toString();
    String url = toUrl(channel);

    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpPost post = new HttpPost(url);
      post.setEntity(new StringEntity(serializedEvent));
      CloseableHttpResponse response = http.execute(post);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }
  }

  protected CloudEvent waitForResponseEvent(String outgoingChannel) {
    String endpoint = toEndpoint(outgoingChannel);
    LoggedRequest response = waitForResponseRequest(endpoint);
    byte[] body = response.getBody();
    return new CloudEventJsonDeserializer().deserialize(Buffer.buffer(body));
  }

  private static LoggedRequest waitForResponseRequest(String endpoint) {
    AtomicReference<LoggedRequest> result = new AtomicReference<>();
    await().untilAsserted(() -> {
      List<LoggedRequest> requests = WireMock.findAll(postRequestedFor(urlEqualTo(endpoint)));
      assertThat(requests).hasSize(1);
      result.set(requests.getFirst());
    });
    return result.get();
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