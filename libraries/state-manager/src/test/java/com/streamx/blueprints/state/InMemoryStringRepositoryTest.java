package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
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
    StateRepository<String> repository = createRepository();
    repository.put(key, value);

    // then
    assertThat(repository.get(key)).isEqualTo(value);

    // and: should reopen existing repository
    StateRepository<String> otherRepositoryInstance = createRepository();
    assertThat(otherRepositoryInstance.get(key)).isEqualTo(value);

    // cleanup
    repository.remove(key);
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
    StateRepository<String> repository = createRepository();
    addData(repository, keys, values);

    // then
    Map<String, String> retrievedData = getRepositoryEntries(repository);

    assertThat(retrievedData.keySet())
        .containsExactlyInAnyOrderElementsOf(keys);

    assertThat(retrievedData.values())
        .containsExactlyInAnyOrderElementsOf(values);

    // cleanup
    repository.clear();
  }

  private InMemoryRepository<String> createRepository() {
    return createRepository(String.class, "strings");
  }
}
