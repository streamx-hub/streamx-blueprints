package com.streamx.blueprints.state.repository.rocksdb;

import com.streamx.blueprints.state.PropertyNames;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

@ApplicationScoped
public class RocksDbManager {

  static final Map<String, RocksDB> rocksDbMap = new ConcurrentHashMap<>();
  private static final String DEFAULT_ROCKSDB_PATH = "/tmp/rocksdb";
  private Options options;

  @PostConstruct
  public void init() {
    RocksDB.loadLibrary();
    options = new Options().setCreateIfMissing(true);
  }

  public RocksDB getOrCreateDb(Config config, String instanceId, String identifier) {
    File rocksDbDir = initDbDir(config, instanceId, identifier);
    String rocksDbDirPath = normalizePath(rocksDbDir);
    return rocksDbMap.computeIfAbsent(rocksDbDirPath, path -> {
      try {
        return RocksDB.open(options, path);
      } catch (RocksDBException e) {
        throw new RuntimeException("Unable to open RocksDB at path " + path, e);
      }
    });
  }

  private static String normalizePath(File rocksDbDir) {
    return rocksDbDir.toPath().toAbsolutePath().normalize().toString();
  }

  private static File initDbDir(Config config, String instanceId, String identifier) {
    File instanceDbsDir = getInstanceDbsDir(config, instanceId);
    File dbDir = new File(instanceDbsDir, identifier);

    try {
      FileUtils.forceMkdir(dbDir);
      return dbDir;
    } catch (IOException ex) {
      throw new RuntimeException("Cannot create RocksDB directory at " + dbDir, ex);
    }
  }

  private static File getInstanceDbsDir(Config config, String instanceId) {
    String rocksDbRootDir = config.getOptionalValue(PropertyNames.ROCKSDB_PATH, String.class)
        .orElse(DEFAULT_ROCKSDB_PATH);
    return new File(rocksDbRootDir, instanceId);
  }

  @PreDestroy
  public void closeAll() {
    rocksDbMap.values().forEach(RocksDB::close);
    options.close();
  }

}
