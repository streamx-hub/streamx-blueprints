package com.streamx.blueprints.state.repository.rocksdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamx.blueprints.state.BaseConfigTest;
import com.streamx.blueprints.state.PropertyNames;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RocksDbManagerTest extends BaseConfigTest {

  @Test
  void shouldNotAllowInvalidRocksDbIdentifier() {
    assertThatThrownBy(() -> RocksDbManager.getOrCreateDb(config, "a/b/c"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid identifier: a/b/c - only letters, digits and dashes allowed");
  }

  @Test
  void shouldNotAllowCreatingRocksDbOnPathTakenByExistingFile() throws IOException {
    // given
    setConfigProperty(PropertyNames.STATE_ROCKSDB_PATH, "target/foo");
    setConfigProperty("streamx.service.instance-id", "service-1");

    String expectedRocksDbDir = "target/foo/service-1/db-1";
    FileUtils.writeStringToFile(new File(expectedRocksDbDir), "text file content", UTF_8);

    // when & then
    assertThatThrownBy(() -> RocksDbManager.getOrCreateDb(config, "db-1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Cannot create RocksDB directory at target/foo/service-1/db-1")
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("Cannot create directory 'target/foo/service-1/db-1'.");
  }
}