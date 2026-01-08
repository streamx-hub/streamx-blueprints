package com.streamx.blueprints.state.repository.rocksdb;

import com.streamx.blueprints.state.PropertyNames;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public final class RocksDbManager {

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9-]+$");

  private static final Map<String, RocksDB> rocksDbMap = new ConcurrentHashMap<>();
  private static final Options options = new Options().setCreateIfMissing(true);

  private RocksDbManager() {
    // no instances
  }

  public static RocksDB getOrCreateDb(Config config, String identifier) {
    File rocksDbDir = initDatabaseDir(config, identifier);
    return rocksDbMap.computeIfAbsent(rocksDbDir.getAbsolutePath(), path -> {
      try {
        return RocksDB.open(options, path);
      } catch (RocksDBException e) {
        throw new RuntimeException("Unable to open RocksDb at path " + path, e);
      }
    });
  }

  private static File initDatabaseDir(Config config, String identifier) {
    validateIdentifier(identifier);

    String rocksDbRootDir = config.getOptionalValue(PropertyNames.STATE_ROCKSDB_PATH, String.class)
        .orElse("/tmp/rocksdb");
    String instanceId = config.getOptionalValue("streamx.service.instance-id", String.class)
        .orElse("unnamed");

    File instanceDatabasesDir = new File(rocksDbRootDir, instanceId);
    File databaseDir = new File(instanceDatabasesDir, identifier);

    try {
      FileUtils.forceMkdir(databaseDir);
      return databaseDir;
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot create RocksDB directory at " + databaseDir, ex);
    }
  }

  private static void validateIdentifier(String identifier) {
    if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
          "Invalid identifier: " + identifier + " - only letters, digits and dashes allowed");
    }
  }

}
