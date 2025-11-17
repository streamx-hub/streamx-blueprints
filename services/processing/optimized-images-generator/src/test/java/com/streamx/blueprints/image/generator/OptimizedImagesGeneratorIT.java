package com.streamx.blueprints.image.generator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.OptimizedAsset;
import com.streamx.blueprints.image.generator.OptimizedImagesGeneratorIT.WireMockProfile;
import com.streamx.reactive.messaging.http.CloudEventJsonDeserializer;
import com.streamx.reactive.messaging.http.CloudEventJsonSerializer;
import io.cloudevents.CloudEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.buffer.Buffer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.FileUtils;
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
public class OptimizedImagesGeneratorIT {

  private static final String INCOMING_ASSETS_URL = "http://localhost:8081/"
                                                    + Channels.INCOMING_ASSETS;
  private static final String OPTIMIZED_ASSETS_ENDPOINT = "/" + Channels.OPTIMIZED_ASSETS;

  // will be injected automatically when the test class is annotated with @ConnectWireMock
  WireMock wiremock;

  @BeforeAll
  static void setEventSource() {
    System.setProperty("quarkus.application.name",
        OptimizedImagesGeneratorIT.class.getSimpleName());
  }

  @BeforeEach
  void setupEndpointForReceivingOutgoingEvents() {
    wiremock.register(post(urlEqualTo(OPTIMIZED_ASSETS_ENDPOINT))
        .willReturn(aResponse().withStatus(202)));
  }

  @Test
  void shouldGenerateOptimizedImage() throws IOException {
    File testImageFile = new File("src/test/resources/ds.png");
    String key = testImageFile.getName();
    Asset asset = new Asset(FileUtils.readFileToByteArray(testImageFile), "test-image");
    CloudEvent sourceEvent = createPublishAssetEvent(key, asset);
    String serializedEvent = new CloudEventJsonSerializer().serialize(sourceEvent).toString();

    try (CloseableHttpClient http = HttpClients.createDefault()) {
      HttpPost post = new HttpPost(INCOMING_ASSETS_URL);
      post.setEntity(new StringEntity(serializedEvent));
      CloseableHttpResponse response = http.execute(post);
      assertThat(response.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
    }

    LoggedRequest response = waitForResponseRequest();
    byte[] body = response.getBody();
    CloudEvent outgoingEvent = new CloudEventJsonDeserializer().deserialize(Buffer.buffer(body));
    assertOutgoingEvent(outgoingEvent, sourceEvent, asset);
  }

  private static CloudEvent createPublishAssetEvent(String key, Asset asset) {
    return CloudEventUtils.eventWithData(key, Asset.TYPE_PUBLISHED, asset);
  }

  private static LoggedRequest waitForResponseRequest() {
    AtomicReference<LoggedRequest> result = new AtomicReference<>();
    await().untilAsserted(() -> {
      List<LoggedRequest> requests = WireMock.findAll(
          postRequestedFor(urlEqualTo(OPTIMIZED_ASSETS_ENDPOINT)));
      assertThat(requests).hasSize(1);
      result.set(requests.getFirst());
    });
    return result.get();
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      Asset sourceAsset) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("optimized-images-generator");
    assertThat(outgoingEvent.getSubject())
        .isNotEqualTo(sourceEvent.getSubject())
        .endsWith("-optimized.webp");
    assertThat(outgoingEvent.getType()).isEqualTo(OptimizedAsset.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertThat(outgoingEvent.getDataContentType()).isEqualTo(sourceEvent.getDataContentType());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, OptimizedAsset.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(sourceAsset.getType());
    assertThat(outgoingResource.getContent()).isNotEqualTo(sourceAsset.getContent());
    assertThat(outgoingResource.getOriginalPath()).isEqualTo(sourceEvent.getSubject());
  }

  public static class WireMockProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "mp.messaging.outgoing." + Channels.OPTIMIZED_ASSETS + ".url",
          "http://" + getContainerLocalhost() + ":${quarkus.wiremock.devservices.port}"
          + OPTIMIZED_ASSETS_ENDPOINT,
          "streamx.blueprints.optimized-images-generator.processed-image-path-pattern",
          ".*"
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