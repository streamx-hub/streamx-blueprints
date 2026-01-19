package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryFactoryTest extends BaseConfigTest {

  @Test
  void shouldNotAllowInvalidServiceInstanceId() {
    setConfigProperty(PropertyNames.SERVICE_INSTANCE_ID, "a/b/c");
    assertThatThrownBy(() -> RepositoryFactory.createRepository(String.class, "pages"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid instanceId: a/b/c - only letters, digits, dashes and dots allowed");
  }

  @Test
  void shouldNotAllowInvalidIdentifier() {
    setConfigProperty(PropertyNames.SERVICE_INSTANCE_ID, "service1");
    assertThatThrownBy(() -> RepositoryFactory.createRepository(String.class, "d/e/f"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid identifier: d/e/f - only letters, digits, dashes and dots allowed");
  }
}