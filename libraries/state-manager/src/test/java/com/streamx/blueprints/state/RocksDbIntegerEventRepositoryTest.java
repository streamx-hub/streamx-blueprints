package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RocksDbIntegerEventRepositoryTest extends BaseRocksDbRepositoryTest {

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
    repository.remove(key);
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

  @Test
  void shouldNotFailGettingDataIfNoData() {
    // when
    StateRepository<Integer> repository = createRepository();

    // then
    assertThat(repository.get("abc")).isNull();
    assertThat(repository.entries()).isEmpty();
  }

  @Test
  void shouldFailRemovingNullKey() {
    // when
    StateRepository<Integer> repository = createRepository();

    // then
    assertThatThrownBy(() -> repository.remove(null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Error removing entry with key null from RocksDB")
        .hasRootCauseInstanceOf(NullPointerException.class);
  }

  private StateRepository<Integer> createRepository() {
    return createRepository(Integer.class, "integers");
  }
}
