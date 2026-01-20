package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryFactoryTest extends BaseConfigTest {

  private final RepositoryFactory repositoryFactory = new RepositoryFactory();

  @Test
  void shouldNotAllowInvalidServiceInstanceId() {
    setConfigProperty(PropertyNames.SERVICE_INSTANCE_ID, "a/b/c");
    assertThatThrownBy(() -> repositoryFactory.getOrCreate("pages"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid instanceId: a/b/c - only letters, digits, dashes and dots allowed");
  }

  @Test
  void shouldNotAllowInvalidIdentifier() {
    setConfigProperty(PropertyNames.SERVICE_INSTANCE_ID, "service1");
    assertThatThrownBy(() -> repositoryFactory.getOrCreate("d/e/f"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid identifier: d/e/f - only letters, digits, dashes and dots allowed");
  }
}