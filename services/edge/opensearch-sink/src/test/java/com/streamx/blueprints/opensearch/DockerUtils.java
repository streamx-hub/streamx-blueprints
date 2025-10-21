package com.streamx.blueprints.opensearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

final class DockerUtils {

  private static final Logger log = LoggerFactory.getLogger(OpenSearchContainer.class);

  private DockerUtils() {
    // no instances
  }

  public static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      log.warn("Docker is not available", e);
      return false;
    }
  }
}
