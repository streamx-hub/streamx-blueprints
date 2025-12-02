package com.streamx.blueprints.opensearch.sink;

import org.jboss.logging.Logger;
import org.testcontainers.DockerClientFactory;

final class DockerUtils {

  private static final Logger log = Logger.getLogger(DockerUtils.class);
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
