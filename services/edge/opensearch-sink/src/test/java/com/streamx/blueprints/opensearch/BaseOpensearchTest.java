package com.streamx.blueprints.opensearch;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.BeforeAll;

@QuarkusTestResource(OpenSearchContainer.class)
public abstract class BaseOpensearchTest {

  @BeforeAll
  static void assumeDockerIsAvailable() {
    assumeTrue(
        DockerUtils.isDockerAvailable,
        "Not able to run opensearch docker container"
    );
  }

}
