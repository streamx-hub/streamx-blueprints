package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseStateRepositoryTest extends BaseConfigTest {

  @BeforeEach
  void initRepositoryFactory() {
    setConfigProperty(PropertyNames.SERVICE_INSTANCE_ID, getClass().getName());
  }

  protected <T> void addData(StateRepository<T> repository, List<String> keys, List<T> values) {
    assertThat(keys).hasSameSizeAs(values);
    for (int i = 0; i < values.size(); i++) {
      repository.put(keys.get(i), values.get(i));
    }
  }

  protected <T> Map<String, T> getRepositoryEntries(StateRepository<T> repository) {
    return repository
        .entries()
        .collect(Collectors.toMap(
            Entry::getKey,
            Entry::getValue));
  }
}