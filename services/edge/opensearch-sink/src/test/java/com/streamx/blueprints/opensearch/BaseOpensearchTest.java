package com.streamx.blueprints.opensearch;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;

public abstract class BaseOpensearchTest {

  @BeforeAll
  static void assumeDockerIsAvailable() {
    assumeTrue(
        DockerUtils.isDockerAvailable(),
        "Not able to run opensearch docker container"
    );
  }

}
