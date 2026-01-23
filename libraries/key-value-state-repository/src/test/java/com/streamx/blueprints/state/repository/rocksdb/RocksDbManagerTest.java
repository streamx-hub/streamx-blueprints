package com.streamx.blueprints.state.repository.rocksdb;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamx.blueprints.state.BaseConfigTest;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RocksDbManagerTest extends BaseConfigTest {

  private final RocksDbManager rocksDbManager = new RocksDbManager();

  @BeforeEach
  void initManager() {
    rocksDbManager.init();
  }

  @Test
  void shouldNotAllowCreatingRocksDbOnPathTakenByExistingFile() throws IOException {
    // given
    configureRocksDbPath("target/foo");
    configureServiceInstanceId("service-1");

    String expectedRocksDbDir = "target/foo/service-1/db-1".replace("/", File.separator);
    FileUtils.writeStringToFile(new File(expectedRocksDbDir), "text file content", UTF_8);

    // when & then
    assertThatThrownBy(() -> rocksDbManager.getOrCreateDb(config, "service-1", "db-1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Cannot create RocksDB directory at " + expectedRocksDbDir)
        .hasRootCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("Cannot create directory '" + expectedRocksDbDir + "'.");
  }
}