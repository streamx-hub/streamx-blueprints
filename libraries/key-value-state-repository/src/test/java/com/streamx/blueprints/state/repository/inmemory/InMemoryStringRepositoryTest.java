package com.streamx.blueprints.state.repository.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryStringRepositoryTest extends BaseInMemoryRepositoryTest {

  @Test
  void shouldReturnInsertedValue() {
    // given
    String key = "item-1";
    String value = "value-1";

    // when
    InMemoryRepository<String> repository = createRepository();
    repository.put(key, value);

    // then
    assertThat(repository.get(key)).isEqualTo(value);

    // and: should reopen existing repository
    InMemoryRepository<String> otherRepositoryInstance = createRepository();
    assertThat(otherRepositoryInstance.get(key)).isEqualTo(value);
  }

  @Test
  void shouldReturnStreamOfInsertedData() {
    // given
    int itemsCount = 10;
    List<String> keys = IntStream.rangeClosed(1, itemsCount)
        .mapToObj(i -> "item#" + i)
        .toList();
    List<String> values = IntStream.rangeClosed(1, itemsCount)
        .mapToObj(i -> "value#" + i)
        .toList();

    // when
    InMemoryRepository<String> repository = createRepository();
    addData(repository, keys, values);

    // then
    Map<String, String> retrievedData = getRepositoryEntries(repository);

    assertThat(retrievedData.keySet())
        .containsExactlyInAnyOrderElementsOf(keys);

    assertThat(retrievedData.values())
        .containsExactlyInAnyOrderElementsOf(values);
  }

  @Test
  void shouldRemoveByValue() {
    // given
    InMemoryRepository<String> repository = createRepository();
    repository.put("key-1", "A");
    repository.put("key-2", "B");
    repository.put("key-3", "B");
    repository.put("key-4", "D");

    // when
    repository.removeByValue("B");

    // then
    assertThat(getRepositoryEntries(repository))
        .hasSize(2)
        .containsEntry("key-1", "A")
        .containsEntry("key-4", "D");
  }

  @Test
  void repositoriesWithSameIdentifierButFromDifferentServicesShouldBeIsolated() {
    // given
    configureServiceInstanceId("service-1");
    InMemoryRepository<String> service1Numbers = createRepository();

    configureServiceInstanceId("service-2");
    InMemoryRepository<String> service2Numbers = createRepository();

    // when
    service1Numbers.put("key", "A");
    service2Numbers.put("key", "B");

    // then
    assertThat(service1Numbers.get("key")).isEqualTo("A");
    assertThat(service2Numbers.get("key")).isEqualTo("B");
  }

  @Test
  void repositoriesWithSameIdentifierAndSameServiceShouldBeSynchronized() {
    // given
    configureServiceInstanceId("service");
    InMemoryRepository<String> repository1 = createRepository();
    InMemoryRepository<String> repository2 = createRepository();

    // when
    repository1.put("key-1", "A");
    repository1.put("key-2", "A");
    repository2.put("key-1", "B");

    // then
    assertThat(repository1.get("key-1")).isEqualTo("B");
    assertThat(repository1.get("key-2")).isEqualTo("A");

    assertThat(repository2.get("key-1")).isEqualTo("B");
    assertThat(repository2.get("key-2")).isEqualTo("A");
  }

  private InMemoryRepository<String> createRepository() {
    return createRepository("strings", String.class);
  }
}
