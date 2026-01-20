package com.streamx.blueprints.state.repository.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.BaseStateRepositoryTest;
import com.streamx.blueprints.state.PropertyNames;
import com.streamx.blueprints.state.RepositoryFactory;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseInMemoryRepositoryTest extends BaseStateRepositoryTest {

  @BeforeEach
  void init() {
    setConfigProperty(PropertyNames.STATE_BACKEND, "in-memory");
  }

  @AfterEach
  void clearRepository() {
    String serviceInstanceId = getConfigProperty(PropertyNames.SERVICE_INSTANCE_ID);
    for (String fullIdentifier : InMemoryRepositoryManager.repositories.keySet()) {
      String repositoryInstanceId = StringUtils.substringBefore(fullIdentifier, "/");
      if (repositoryInstanceId.equals(serviceInstanceId)) {
        var repository = InMemoryRepositoryManager.repositories.get(fullIdentifier);
        repository.entries()
            .map(Entry::getKey)
            .forEach(repository::remove);
      }
    }
  }

  protected <T> InMemoryRepository<T> createRepository(String identifier) {
    var repository = repositoryFactory.getOrCreate(identifier);
    assertThat(repository).isInstanceOf(InMemoryRepository.class);
    return (InMemoryRepository<T>) repository;
  }
}
