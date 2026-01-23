package com.streamx.blueprints.state.repository.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.state.BaseStateRepositoryTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class BaseRocksDbRepositoryTest extends BaseStateRepositoryTest {

  private static final File dbPath = new File("target/rocksdb-test");

  @BeforeEach
  void init() {
    configureStateBackend("rocksdb");
    configureRocksDbPath(dbPath.getAbsolutePath());
  }

  @AfterEach
  void dropRocksDb() throws IOException {
    if (dbPath.exists()) {
      closeInstanceDbs();

      String serviceInstanceId = readServiceInstanceId();
      FileUtils.deleteDirectory(new File(dbPath, serviceInstanceId));
    }
  }

  protected void closeInstanceDbs() {
    String serviceInstanceId = readServiceInstanceId();
    for (String dbPath : RocksDbManager.rocksDbMap.keySet()) {
      String dbInstanceId = Path.of(dbPath).getParent().getFileName().toString();
      if (dbInstanceId.equals(serviceInstanceId)) {
        RocksDbManager.rocksDbMap.get(dbPath).close();
        RocksDbManager.rocksDbMap.remove(dbPath);
      }
    }
  }

  protected <T> RocksDbRepository<T> createRepository(String identifier, Class<T> valueClass) {
    var repository = repositoryFactory.getOrCreate(identifier, valueClass);
    assertThat(repository).isInstanceOf(RocksDbRepository.class);
    return (RocksDbRepository<T>) repository;
  }
}
