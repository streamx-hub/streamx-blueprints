package com.streamx.blueprints.state.repository.rocksdb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;

class RocksDbInvalidTypeRepositoryTest extends BaseRocksDbRepositoryTest {

  private static class NonJsonSerializableClass {

    private final int intVal;
    private final String stringVal;

    private NonJsonSerializableClass(int intVal, String stringVal) {
      this.intVal = intVal;
      this.stringVal = stringVal;
    }
  }

  @Test
  void shouldFailInsertingNonSerializableValue() {
    // given
    String key = "key-1";
    NonJsonSerializableClass value = new NonJsonSerializableClass(1, "abc");

    // when & then
    RocksDbRepository<NonJsonSerializableClass> repository = createRepository();
    assertThatThrownBy(() -> repository.put(key, value))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Error putting entry with key key-1 to RocksDB")
        .hasRootCauseInstanceOf(JsonMappingException.class)
        .hasRootCauseMessage(
            "No serializer found for class " + NonJsonSerializableClass.class.getName()
            + " and no properties discovered to create BeanSerializer"
            + " (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS)"
        );
  }

  private RocksDbRepository<NonJsonSerializableClass> createRepository() {
    return createRepository("data", NonJsonSerializableClass.class);
  }
}
