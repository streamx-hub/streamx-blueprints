package com.streamx.blueprints.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.repository.rocksdb.RocksDbManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseRocksDbRepositoryTest extends BaseStateRepositoryTest {

  private static final File dbPath = new File("target/rocksdb-test");

  @BeforeEach
  void init() {
    setConfigProperty(PropertyNames.STATE_BACKEND, "rocksdb");
    setConfigProperty(PropertyNames.STATE_ROCKSDB_PATH, dbPath.getAbsolutePath());
  }

  @AfterEach
  void dropRocksDb() throws IOException {
    if (dbPath.exists()) {
      RocksDbManager.closeInstanceDbs(config);

      String serviceInstanceId = config
          .getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
          .orElseThrow();

      FileUtils.deleteDirectory(new File(dbPath, serviceInstanceId));
    }
  }

  protected <T> RocksDbRepository<T> createRepository(Class<T> valueClass, String identifier) {
    var repository = RepositoryFactory.createRepository(config, valueClass, identifier);
    assertThat(repository).isInstanceOf(RocksDbRepository.class);
    return (RocksDbRepository<T>) repository;
  }
}
