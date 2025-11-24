package com.streamx.blueprints.test.integration;

import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChannelsReader {

  private static final File TEST_PROPERTIES_FILE =
      new File("src/test/resources/application.properties");

  private static final Pattern CHANNEL_CONNECTOR_PROPERTY_NAME_PATTERN =
      Pattern.compile("^mp\\.messaging\\.outgoing\\.(.+)\\.connector$");

  static final List<String> OUTGOING_CHANNELS = loadOutgoingChannels();

  private ChannelsReader() {
    // no instances
  }

  private static List<String> loadOutgoingChannels() {
    Properties testProperties = loadTestProperties();
    return testProperties.stringPropertyNames().stream()
        .map(CHANNEL_CONNECTOR_PROPERTY_NAME_PATTERN::matcher)
        .filter(Matcher::find)
        .map(matcher -> matcher.group(1))
        .toList();
  }

  private static Properties loadTestProperties() {
    Properties testProperties = new Properties();
    try (InputStream inputStream = new FileInputStream(TEST_PROPERTIES_FILE)) {
      testProperties.load(inputStream);
    } catch (IOException ex) {
      return fail("Error loading test properties", ex);
    }
    return testProperties;
  }
}