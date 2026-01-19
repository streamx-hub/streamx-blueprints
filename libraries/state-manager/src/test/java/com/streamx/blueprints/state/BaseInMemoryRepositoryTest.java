package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseInMemoryRepositoryTest extends BaseStateRepositoryTest {

  @BeforeEach
  void init() {
    setConfigProperty(PropertyNames.STATE_BACKEND, "in-memory");
  }

  protected <T> InMemoryRepository<T> createRepository(Class<T> valueClass, String identifier) {
    var repository = RepositoryFactory.createRepository(valueClass, identifier);
    assertThat(repository).isInstanceOf(InMemoryRepository.class);
    return (InMemoryRepository<T>) repository;
  }
}
