package com.streamx.blueprints.state.repository.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RocksDbByteArrayRepositoryTest extends BaseRocksDbRepositoryTest {

  @Test
  void shouldReturnInsertedValue() {
    // given
    String key = "key-1";
    byte[] value = {0, 1, 2};

    // when
    RocksDbRepository<byte[]> repository = createRepository();
    repository.put(key, value);

    // then
    assertThat(repository.get(key)).containsExactly(0, 1, 2);

    // and: should reopen existing repository
    RocksDbRepository<byte[]> otherRepositoryInstance = createRepository();
    assertThat(otherRepositoryInstance.get(key)).containsExactly(0, 1, 2);
  }

  private RocksDbRepository<byte[]> createRepository() {
    return createRepository("byte-arrays", byte[].class);
  }
}
