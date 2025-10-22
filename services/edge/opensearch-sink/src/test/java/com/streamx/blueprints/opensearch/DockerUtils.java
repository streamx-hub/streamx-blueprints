package com.streamx.blueprints.opensearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

final class DockerUtils {

  private static final Logger log = LoggerFactory.getLogger(OpensearchContainer.class);
  static final boolean isDockerAvailable = isDockerAvailable();

  private DockerUtils() {
    // no instances
  }

  private static boolean isDockerAvailable() {
    try {
      DockerClientFactory.instance().client();
      return true;
    } catch (Exception e) {
      log.warn("Docker is not available", e);
      return false;
    }
  }
}
