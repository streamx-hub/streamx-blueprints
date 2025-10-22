package com.streamx.blueprints.opensearch;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Collections;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;

public class OpenSearchContainer implements QuarkusTestResourceLifecycleManager {

  private static final String DOCKER_IMAGE_NAME = "opensearchproject/opensearch:2.16.0";
  private static final int EXPOSED_PORT = 9200;

  private final GenericContainer<?> container = new GenericContainer<>(DOCKER_IMAGE_NAME)
      .withEnv("DISABLE_SECURITY_PLUGIN", "true")
      .withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "admin")
      .withEnv("discovery.type", "single-node")
      .withExposedPorts(EXPOSED_PORT);

  @Override
  public Map<String, String> start() {
    if (DockerUtils.isDockerAvailable) {
      container.start();

      String host = container.getHost();
      Integer port = container.getMappedPort(EXPOSED_PORT);
      return Map.of("quarkus.elasticsearch.hosts", host + ":" + port);
    }
    return Collections.emptyMap();
  }

  @Override
  public void stop() {
    if (DockerUtils.isDockerAvailable) {
      container.stop();
    }
  }
}