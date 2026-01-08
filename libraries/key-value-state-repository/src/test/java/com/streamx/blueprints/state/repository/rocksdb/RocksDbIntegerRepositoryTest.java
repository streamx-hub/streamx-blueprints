package com.streamx.blueprints.state.repository.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RocksDbIntegerRepositoryTest extends BaseRocksDbRepositoryTest {

  @Test
  void shouldReturnInsertedValue() {
    // given
    String key = "key-1";
    int value = 123;

    // when
    RocksDbRepository<Integer> repository = createRepository();
    repository.put(key, value);

    // then
    assertThat(repository.get(key)).isEqualTo(value);

    // and: should reopen existing repository
    RocksDbRepository<Integer> otherRepositoryInstance = createRepository();
    assertThat(otherRepositoryInstance.get(key)).isEqualTo(value);
  }

  @Test
  void shouldReturnStreamOfInsertedData() {
    // given
    int itemsCount = 9;
    List<String> keys = IntStream.rangeClosed(1, itemsCount)
        .mapToObj(i -> "key#" + i)
        .toList();
    List<Integer> values = IntStream.rangeClosed(1, itemsCount)
        .boxed()
        .toList();

    // when
    RocksDbRepository<Integer> repository = createRepository();
    addData(repository, keys, values);

    // then
    Map<String, Integer> retrievedEvents = getRepositoryEntries(repository);

    assertThat(retrievedEvents.keySet())
        .containsExactlyElementsOf(keys);

    assertThat(retrievedEvents.values())
        .containsExactlyElementsOf(values);
  }

  @Test
  void shouldRemoveByValue() {
    // given
    RocksDbRepository<Integer> repository = createRepository();
    repository.put("key-1", 1);
    repository.put("key-2", 2);
    repository.put("key-3", 2);
    repository.put("key-4", 4);

    // when
    repository.removeByValue(2);

    // then
    assertThat(getRepositoryEntries(repository))
        .hasSize(2)
        .containsEntry("key-1", 1)
        .containsEntry("key-4", 4);
  }

  @Test
  void repositoriesWithSameIdentifierButFromDifferentServicesShouldBeIsolated() {
    // given
    configureServiceInstanceId("service-1");
    RocksDbRepository<Integer> service1Numbers = createRepository();

    configureServiceInstanceId("service-2");
    RocksDbRepository<Integer> service2Numbers = createRepository();

    // when
    service1Numbers.put("key", 1);
    service2Numbers.put("key", 2);

    // then
    assertThat(service1Numbers.get("key")).isEqualTo(1);
    assertThat(service2Numbers.get("key")).isEqualTo(2);
  }

  @Test
  void repositoriesWithSameIdentifierAndSameServiceShouldBeSynchronized() {
    // given
    configureServiceInstanceId("service");
    RocksDbRepository<Integer> repository1 = createRepository();
    RocksDbRepository<Integer> repository2 = createRepository();

    // when
    repository1.put("key-1", 1);
    repository1.put("key-2", 1);
    repository2.put("key-1", 2);

    // then
    assertThat(repository1.get("key-1")).isEqualTo(2);
    assertThat(repository1.get("key-2")).isEqualTo(1);

    assertThat(repository2.get("key-1")).isEqualTo(2);
    assertThat(repository2.get("key-2")).isEqualTo(1);
  }

  @Test
  void shouldNotFailGettingDataIfNoData() {
    // when
    RocksDbRepository<Integer> repository = createRepository();

    // then
    assertThat(repository.get("abc")).isNull();
    assertThat(repository.entries()).isEmpty();
  }

  @Test
  void shouldFailRemovingNullKey() {
    // when
    RocksDbRepository<Integer> repository = createRepository();

    // then
    assertThatThrownBy(() -> repository.remove(null))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Error removing entry with key null from RocksDB")
        .hasRootCauseInstanceOf(NullPointerException.class);
  }

  private RocksDbRepository<Integer> createRepository() {
    return createRepository("integers", Integer.class);
  }
}
