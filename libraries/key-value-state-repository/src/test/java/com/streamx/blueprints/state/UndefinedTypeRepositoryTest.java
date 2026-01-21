package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UndefinedTypeRepositoryTest extends BaseStateRepositoryTest {

  @BeforeEach
  void init() {
    configureStateBackend(null);
  }

  @Test
  void shouldCreateInMemoryRepository() {
    StateRepository<String> repository = repositoryFactory.getOrCreate("strings", String.class);
    assertThat(repository).isInstanceOf(InMemoryRepository.class);
  }

}
