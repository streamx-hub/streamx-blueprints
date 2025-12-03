package com.streamx.blueprints.opensearch.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import com.streamx.blueprints.test.integration.ProcessRunner;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Properties;
import org.apache.http.client.utils.URIBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class OpensearchTestContainer {

  private static final String DOCKER_IMAGE_NAME = "opensearchproject/opensearch:2.16.0";
  private static final String HOST_NAME_IN_NETWORK = "opensearch";
  private static final int EXPOSED_PORT = 9200;
  private static final String NETWORK_NAME = readNetworkName();

  private static final Network network = Network.builder()
      .createNetworkCmdModifier(cmd -> cmd.withName(NETWORK_NAME))
      .build();

  private static final GenericContainer<?> container = new GenericContainer<>(DOCKER_IMAGE_NAME)
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "admin")
      .withEnv("discovery.type", "single-node")
      .withExposedPorts(EXPOSED_PORT)
      .withNetwork(network)
      .withNetworkAliases(HOST_NAME_IN_NETWORK);

  public static void start() {
    container.start();
    System.setProperty("quarkus.elasticsearch.hosts", externalHostAndPort());
  }

  public static String externalHostAndPort() {
    return getHost() + ":" + getPort();
  }

  public static String internalHostAndPort() {
    return HOST_NAME_IN_NETWORK + ":" + EXPOSED_PORT;
  }

  public static String getHost() {
    assertThat(isRunning()).isTrue();
    return container.getHost();
  }

  public static int getPort() {
    assertThat(isRunning()).isTrue();
    return container.getMappedPort(EXPOSED_PORT);
  }

  private static boolean isRunning() {
    return container.isRunning();
  }

  public static void stop() {
    if (isRunning()) {
      container.stop();
      network.close();
    }
  }

  private static String readNetworkName() {
    Properties properties = new Properties();
    try (var inputStream = new FileInputStream("src/test/resources/application.properties")) {
      properties.load(inputStream);
      return properties.getProperty("quarkus.test.container.network");
    } catch (IOException ex) {
      return fail("Error loading test properties", ex);
    }
  }

  public static void waitUntilPreviousInstanceExited() {
    await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
      assertCommandDoesNotPrint("docker ps", DOCKER_IMAGE_NAME);
      assertCommandDoesNotPrint("docker network ls", NETWORK_NAME);
    });
  }

  private static void assertCommandDoesNotPrint(String command, String token) {
    assertThat(ProcessRunner.readProcessOutput(command))
        .doesNotContain(token);
  }

  public static String getSearchUrl(String pagePath) throws URISyntaxException {
    return new URIBuilder()
        .setScheme("http")
        .setHost(getHost())
        .setPort(getPort())
        .setPath("/default/_search")
        .setCustomQuery("q=_id:\"" + pagePath + "\"")
        .build()
        .toString();
  }
}