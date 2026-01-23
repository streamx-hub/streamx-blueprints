package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryFactoryTest extends BaseConfigTest {

  private final RepositoryFactory repositoryFactory = new RepositoryFactory();

  @Test
  void shouldNotAllowInvalidServiceInstanceId() {
    configureServiceInstanceId("a/b/c");
    assertThatThrownBy(() -> repositoryFactory.getOrCreate("pages", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid instanceId: a/b/c - only letters, digits, dashes and dots allowed");
  }

  @Test
  void shouldNotAllowInvalidIdentifier() {
    configureServiceInstanceId("service1");
    assertThatThrownBy(() -> repositoryFactory.getOrCreate("d/e/f", String.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid identifier: d/e/f - only letters, digits, dashes and dots allowed");
  }
}