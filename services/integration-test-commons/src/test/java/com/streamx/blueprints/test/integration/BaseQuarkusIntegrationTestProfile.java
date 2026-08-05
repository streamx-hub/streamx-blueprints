package com.streamx.blueprints.test.integration;

import static com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest.propertiesForOutgoingChannels;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.config.source.yaml.YamlConfigSource;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BaseQuarkusIntegrationTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    Map<String, String> properties = new HashMap<>();
    properties.put("quarkus.wiremock.devservices.enabled", "true");
    properties.put("streamx.blueprints.key-value-state-repository.backend", "rocksdb");
    properties.put("streamx.blueprints.key-value-state-repository.rocksdb.path", "/tmp/rocksdb");
    properties.putAll(propertiesForOutgoingChannels());
    properties.putAll(getServiceConfigProperties());
    return properties;
  }

  protected Map<String, String> getServiceConfigProperties() {
    return Collections.emptyMap();
  }

  protected Map<String, String> getConfigPropertiesFromYaml(String path) {
    try {
      URL yamlUrl = Thread.currentThread()
          .getContextClassLoader()
          .getResource(path);

      if (yamlUrl == null) {
        throw new IllegalStateException("Config not found on classpath: " + path);
      }

      YamlConfigSource source = new YamlConfigSource(yamlUrl);
      return source.getPropertyNames()
          .stream()
          .collect(Collectors.toMap(
              Function.identity(),
              source::getValue
          ));
    } catch (Exception e) {
      throw new RuntimeException("Failed to load YAML config overrides", e);
    }
  }
}
