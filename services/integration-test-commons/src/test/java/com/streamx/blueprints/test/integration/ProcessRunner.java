package com.streamx.blueprints.test.integration;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public final class ProcessRunner {

  private ProcessRunner() {
    // no instances
  }

  public static String readProcessOutput(String command) {
    String[] words = command.split(" ");
    ProcessBuilder builder = new ProcessBuilder(words);
    builder.redirectErrorStream(true);  // merge STDOUT + STDERR
    try {
      Process process = builder.start();
      return IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      return fail("Error reading output of command", ex);
    }
  }

}