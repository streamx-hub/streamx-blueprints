package com.streamx.blueprints.state.repository.rocksdb;

import com.streamx.blueprints.state.PropertyNames;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public final class RocksDbManager {

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9-.]+$");
  private static final String IDENTIFIER_PATTERN_DESCRIPTION =
      "only letters, digits, dashes and dots allowed";

  private static final Map<String, RocksDB> rocksDbMap = new ConcurrentHashMap<>();
  private static final Options options = new Options().setCreateIfMissing(true);

  private RocksDbManager() {
    // no instances
  }

  public static RocksDB getOrCreateDb(Config config, String instanceId, String identifier) {
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
    validateIdentifier(instanceId, "instanceId");
    validateIdentifier(identifier, "identifier");

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
    String rocksDbRootDir = config.getOptionalValue(PropertyNames.STATE_ROCKSDB_PATH, String.class)
        .orElse("/tmp/rocksdb");
    return new File(rocksDbRootDir, instanceId);
  }

  private static void validateIdentifier(String identifier, String fieldName) {
    if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
          "Invalid " + fieldName + ": " + identifier + " - " + IDENTIFIER_PATTERN_DESCRIPTION);
    }
  }

  public static void closeInstanceDbs(Config config) {
    String serviceInstanceId = config
        .getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
        .orElseThrow();
    for (String dbPath : rocksDbMap.keySet()) {
      String dbInstanceId = Path.of(dbPath).getParent().getFileName().toString();
      if (dbInstanceId.equals(serviceInstanceId)) {
        rocksDbMap.get(dbPath).close();
        rocksDbMap.remove(dbPath);
      }
    }
  }

}
