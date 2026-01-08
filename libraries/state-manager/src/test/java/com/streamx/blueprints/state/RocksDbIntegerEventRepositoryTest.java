package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RocksDbIntegerEventRepositoryTest extends BaseStateRepositoryTest {

  @BeforeEach
  void init() {
    File dbPath = new File("target/rocksdb-test");
    FileUtils.deleteQuietly(dbPath);

    setConfigProperty(PropertyNames.STATE_BACKEND, "rocksdb");
    setConfigProperty(PropertyNames.STATE_ROCKSDB_PATH, dbPath.getAbsolutePath());
  }

  @Test
  void shouldReturnInsertedValue() {
    // given
    String key = "key-1";
    int value = 123;

    // when
    StateRepository<Integer> repository = createRepository();
    repository.put(key, value);

    // then
    assertThat(repository.get(key)).isEqualTo(value);

    // and: should reopen existing repository
    StateRepository<Integer> otherRepositoryInstance = createRepository();
    assertThat(otherRepositoryInstance.get(key)).isEqualTo(value);

    // cleanup
    repository.clear();
  }

  @Test
  void shouldReturnStreamOfInsertedData() {
    // given
    int itemsCount = 10;
    List<String> keys = IntStream.rangeClosed(1, itemsCount)
        .mapToObj(i -> "key#" + i)
        .toList();
    List<Integer> values = IntStream.rangeClosed(1, itemsCount)
        .boxed()
        .toList();

    // when
    StateRepository<Integer> repository = createRepository();
    addData(repository, keys, values);

    // then
    Map<String, Integer> retrievedEvents = getRepositoryEntries(repository);

    assertThat(retrievedEvents.keySet())
        .containsExactlyInAnyOrderElementsOf(keys);

    assertThat(retrievedEvents.values())
        .containsExactlyInAnyOrderElementsOf(values);

    // cleanup
    repository.clear();
  }

  private StateRepository<Integer> createRepository() {
    var repository = RepositoryFactory.createRepository(config, Integer.class, "integers");
    assertThat(repository).isInstanceOf(RocksDbRepository.class);
    return repository;
  }
}
