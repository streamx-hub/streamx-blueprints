package com.streamx.blueprints.test.integration;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

public final class DockerLogsRetriever {

  private static final Logger log = Logger.getLogger(DockerLogsRetriever.class);

  private DockerLogsRetriever() {
    // no instances
  }

  public static void printDockerContainerLogs() {
    String dockerPsOutput = ProcessRunner.readProcessOutput("docker ps");
    String containerLine = dockerPsOutput.lines()
        .filter(line -> line.contains("quarkus-integration-test"))
        .findFirst().orElseThrow();
    String containerId = StringUtils.substringBefore(containerLine, " ");

    String logs = ProcessRunner.readProcessOutput("docker logs " + containerId);
    log.info("\n---- DOCKER CONTAINER LOGS ---\n" + logs);
  }

}