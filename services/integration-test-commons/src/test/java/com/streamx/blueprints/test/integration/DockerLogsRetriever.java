package com.streamx.blueprints.test.integration;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

final class DockerLogsRetriever {

  private static final Logger log = Logger.getLogger(DockerLogsRetriever.class);

  private DockerLogsRetriever() {
    // no instances
  }

  static void printDockerContainerLogs() {
    String dockerPsOutput = readProcessOutput("docker", "ps");
    String containerLine = dockerPsOutput.lines()
        .filter(line -> line.contains("quarkus-integration-test"))
        .findFirst().orElseThrow();
    String containerId = StringUtils.substringBefore(containerLine, " ");

    String logs = readProcessOutput("docker", "logs", containerId);
    log.info("\n---- DOCKER CONTAINER LOGS ---\n" + logs);
  }

  private static String readProcessOutput(String... command) {
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);  // merge STDOUT + STDERR
    try {
      Process process = builder.start();
      return IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      return fail("Error reading output of command", ex);
    }
  }

}