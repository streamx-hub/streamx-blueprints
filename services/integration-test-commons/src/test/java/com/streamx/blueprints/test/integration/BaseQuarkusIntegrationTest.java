package com.streamx.blueprints.test.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.streamx.blueprints.test.integration.JsonFormatter.formatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import io.cloudevents.CloudEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.awaitility.core.ConditionTimeoutException;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

@ConnectWireMock
public abstract class BaseQuarkusIntegrationTest {

  protected static final String SERVICE_BASE_URL = "http://localhost:8081";

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

  protected static void sendEvent(CloudEvent cloudEvent, String channel) {
    String serializedEvent = CloudEventsSerialization.serialize(cloudEvent);
    String url = toUrl(channel);
    HttpRequestor.post(url, serializedEvent);
  }

  protected static <T> void sendEvent(String key, String eventType, T data, String channel) {
    CloudEvent event = CloudEventUtils.eventWithData(key, eventType, data);
    sendEvent(event, channel);
  }

  /**
   * Some services are configured in the mesh file to use the same ref for outgoing and incoming
   * channels. Since QuarkusIntegrationTests don't use a mesh file, this method can be used to
   * manually simulate that behavior
   */
  protected void sendFromOutgoingToIncomingChannel(String outgoing, String incoming) {
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
        .map(CloudEventsSerialization::deserialize)
        .toList();
  }

  private List<LoggedRequest> waitForResponseRequests(String endpoint, int totalCount) {
    List<LoggedRequest> results = new LinkedList<>();
    try {
      await().atMost(waitForResponseEventsTimeout()).untilAsserted(() -> {
        List<LoggedRequest> requests = WireMock.findAll(postRequestedFor(urlEqualTo(endpoint)));
        assertThat(requests).hasSize(totalCount);
        results.addAll(requests);
      });
    } catch (ConditionTimeoutException ex) {
      DockerLogsRetriever.printDockerContainerLogs();
      return fail(ex);
    }
    return results;
  }

  protected Duration waitForResponseEventsTimeout() {
    return Duration.ofSeconds(3);
  }

  protected String getUrlContent(String url) {
    AtomicReference<String> content = new AtomicReference<>();
    await().atMost(waitForResponseEventsTimeout()).untilAsserted(() ->
        content.set(HttpRequestor.getUrlContent(url))
    );
    return content.get();
  }

  protected static void assertSameJsons(String actual, String expected) {
    assertThat(formatJson(actual)).isEqualTo(formatJson(expected));
  }

  protected static void assertSameJsons(JsonNode actualNode, String expected,
      Pattern... patternsToRemove) {
    String actualJson = formatJson(actualNode);
    String expectedJson = formatJson(expected);
    for (Pattern pattern : patternsToRemove) {
      actualJson = removePattern(actualJson, pattern);
      expectedJson = removePattern(expectedJson, pattern);
    }
    assertThat(actualJson).isEqualTo(expectedJson);
  }

  private static String removePattern(String string, Pattern pattern) {
    return pattern.matcher(string).replaceAll("");
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
    return SERVICE_BASE_URL + toEndpoint(channel);
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