package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.rocksdb.RocksDbManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseStateRepositoryTest extends BaseConfigTest {

  protected final RepositoryFactory repositoryFactory = new RepositoryFactory();

  @BeforeEach
  void initRepositoryFactory() {
    configureServiceInstanceId(getClass().getName());
    repositoryFactory.rocksDbManager = new RocksDbManager();
    repositoryFactory.rocksDbManager.init();
  }

  protected <T> void addData(StateRepository<T> repository, List<String> keys, List<T> values) {
    assertThat(keys).hasSameSizeAs(values);
    for (int i = 0; i < values.size(); i++) {
      repository.put(keys.get(i), values.get(i));
    }
  }

  protected <T> Map<String, T> getRepositoryEntries(StateRepository<T> repository) {
    LinkedHashMap<String, T> entries = repository
        .entries()
        .collect(Collectors.toMap(
            Entry::getKey,
            Entry::getValue,
            (u, v) -> {
              throw new IllegalStateException("Duplicate keys not expected: " + u);
            },
            LinkedHashMap::new));

    assertThat(entries.keySet()).containsExactlyElementsOf(repository.keys().toList());
    assertThat(entries.values()).containsExactlyElementsOf(repository.values().toList());

    return entries;
  }
}