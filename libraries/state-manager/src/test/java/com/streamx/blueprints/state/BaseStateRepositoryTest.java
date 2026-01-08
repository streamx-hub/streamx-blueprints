package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseStateRepositoryTest {

  protected final Config config = mock(Config.class);

  @BeforeEach
  void initRepositoryFactory() {
    setConfigProperty("streamx.service.instance-id", getClass().getName());
  }

  protected void setConfigProperty(String name, String value) {
    doReturn(Optional.ofNullable(value))
        .when(config)
        .getOptionalValue(name, String.class);
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