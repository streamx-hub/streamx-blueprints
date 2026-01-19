package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UndefinedTypeUserRepositoryTest extends BaseStateRepositoryTest {

  @BeforeEach
  void init() {
    setConfigProperty(PropertyNames.STATE_BACKEND, null);
  }

  record User(String name, int age) {

  }

  @Test
  void shouldReturnInsertedValue() {
    // given
    String key = "user-1";
    User value = new User("John", 87);

    // when
    StateRepository<User> repository = createRepository();
    repository.put(key, value);

    // then
    assertThat(repository.get(key)).isEqualTo(value);

    // and: should reopen existing repository
    StateRepository<User> otherRepositoryInstance = createRepository();
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
    List<User> values = IntStream.rangeClosed(1, itemsCount)
        .mapToObj(i -> new User("name#" + i, i))
        .toList();

    // when
    StateRepository<User> repository = createRepository();
    addData(repository, keys, values);

    // then
    Map<String, User> retrievedData = getRepositoryEntries(repository);

    assertThat(retrievedData.keySet())
        .containsExactlyInAnyOrderElementsOf(keys);

    assertThat(retrievedData.values())
        .containsExactlyInAnyOrderElementsOf(values);

    // cleanup
    repository.clear();
  }

  private StateRepository<User> createRepository() {
    var repository = RepositoryFactory.createRepository(User.class, "users");
    // when repository type is not specified via property - expect in memory as default
    assertThat(repository).isInstanceOf(InMemoryRepository.class);
    return repository;
  }
}
