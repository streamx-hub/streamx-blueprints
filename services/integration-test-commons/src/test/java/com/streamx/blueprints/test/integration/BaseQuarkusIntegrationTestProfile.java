package com.streamx.blueprints.test.integration;

import static com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest.propertiesForOutgoingChannels;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Collections;
import java.util.Map;

public class BaseQuarkusIntegrationTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    Map<String, String> properties = propertiesForOutgoingChannels();
    properties.putAll(getServiceConfigProperties());
    return properties;
  }

  protected Map<String, String> getServiceConfigProperties() {
    return Collections.emptyMap();
  }

}
