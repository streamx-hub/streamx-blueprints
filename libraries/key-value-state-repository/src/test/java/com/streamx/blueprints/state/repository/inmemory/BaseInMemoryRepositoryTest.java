package com.streamx.blueprints.state.repository.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.BaseStateRepositoryTest;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseInMemoryRepositoryTest extends BaseStateRepositoryTest {

  @BeforeEach
  void init() {
    configureStateBackend("in-memory");
  }

  @AfterEach
  void clearRepository() {
    String serviceInstanceId = readServiceInstanceId();
    for (String fullIdentifier : InMemoryRepositoryManager.repositories.keySet()) {
      String repositoryInstanceId = StringUtils.substringBefore(fullIdentifier, "/");
      if (repositoryInstanceId.equals(serviceInstanceId)) {
        var repository = InMemoryRepositoryManager.repositories.get(fullIdentifier);
        Set<String> keys = repository.keys().collect(Collectors.toSet());
        keys.forEach(repository::remove);
      }
    }
  }

  protected <T> InMemoryRepository<T> createRepository(String identifier, Class<T> valueClass) {
    var repository = repositoryFactory.getOrCreate(identifier, valueClass);
    assertThat(repository).isInstanceOf(InMemoryRepository.class);
    return (InMemoryRepository<T>) repository;
  }
}
