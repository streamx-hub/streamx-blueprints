package com.streamx.blueprints.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.testcontainers.containers.GenericContainer;

public class OpensearchContainer {

  private static final String DOCKER_IMAGE_NAME = "opensearchproject/opensearch:2.16.0";
  private static final int EXPOSED_PORT = 9200;

  private static final GenericContainer<?> container = new GenericContainer<>(DOCKER_IMAGE_NAME)
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "admin")
      .withEnv("discovery.type", "single-node")
      .withExposedPorts(EXPOSED_PORT);

  public static void start() {
    container.start();
    System.setProperty("quarkus.elasticsearch.hosts", getHost() + ":" + getPort());
  }

  public static void stop() {
    if (isRunning()) {
      container.stop();
    }
  }

  public static boolean isRunning() {
    return container.isRunning();
  }

  public static String getHost() {
    assertThat(isRunning()).isTrue();
    return container.getHost();
  }

  public static int getPort() {
    assertThat(isRunning()).isTrue();
    return container.getMappedPort(EXPOSED_PORT);
  }
}