package com.streamx.blueprints.state.repository.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.BaseStateRepositoryTest;
import com.streamx.blueprints.state.PropertyNames;
import com.streamx.blueprints.state.RepositoryFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;
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
      closeInstanceDbs(config);

      String serviceInstanceId = getConfigProperty(PropertyNames.SERVICE_INSTANCE_ID);
      FileUtils.deleteDirectory(new File(dbPath, serviceInstanceId));
    }
  }

  protected static void closeInstanceDbs(Config config) {
    String serviceInstanceId = config
        .getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
        .orElseThrow();
    for (String dbPath : RocksDbManager.rocksDbMap.keySet()) {
      String dbInstanceId = Path.of(dbPath).getParent().getFileName().toString();
      if (dbInstanceId.equals(serviceInstanceId)) {
        RocksDbManager.rocksDbMap.get(dbPath).close();
        RocksDbManager.rocksDbMap.remove(dbPath);
      }
    }
  }

  protected <T> RocksDbRepository<T> createRepository(String identifier) {
    var repository = repositoryFactory.getOrCreate(identifier);
    assertThat(repository).isInstanceOf(RocksDbRepository.class);
    return (RocksDbRepository<T>) repository;
  }
}
