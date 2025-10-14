package com.streamx.blueprints.data.collector;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(value = CollectorOutputTypeConfigTest.Configuration.class)
class CollectorOutputTypeConfigTest extends AbstractCollectorTest {

  protected String getExpectedCollectedDataOutputType() {
    return "configured-output-data-type"; // value from config property
  }

  public static class Configuration implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
      return "collectortest,outputtypetest";
    }
  }
}
