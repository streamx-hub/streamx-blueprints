package com.streamx.blueprints.state.sql.repository.sqlite;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class SqliteManager {

  private static final String DEFAULT_SQLITE_PATH = "/tmp/sqlite";
  private static final int DEFAULT_MAX_POOL_SIZE = 4;
  private static final int DEFAULT_BUSY_TIMEOUT_MS = 5_000;

  private final Map<String, AgroalDataSource> dataSourceMap = new ConcurrentHashMap<>();

  public DataSource getOrCreateDb(Config config, String instanceId, String identifier) {
    File dbFile = initDbFile(config, instanceId, identifier);
    String dbPath = dbFile.toPath().toAbsolutePath().normalize().toString();

    int maxPoolSize = config
        .getOptionalValue(PropertyNames.SQLITE_MAX_POOL_SIZE, Integer.class)
        .orElse(DEFAULT_MAX_POOL_SIZE);
    int busyTimeoutMs = config
        .getOptionalValue(PropertyNames.SQLITE_MAX_BUSY_TIMEOUT, Integer.class)
        .orElse(DEFAULT_BUSY_TIMEOUT_MS);

    return dataSourceMap.computeIfAbsent(
        dbPath, path -> createDataSource(path, maxPoolSize, busyTimeoutMs));
  }

  private AgroalDataSource createDataSource(String path, int maxPoolSize, int busyTimeoutMs) {
    String jdbcUrl = "jdbc:sqlite:" + path
        + "?foreign_keys=on"
        + "&journal_mode=WAL"
        + "&busy_timeout=" + busyTimeoutMs;

    AgroalDataSourceConfigurationSupplier configuration =
        new AgroalDataSourceConfigurationSupplier()
            .connectionPoolConfiguration(cp -> cp
                .maxSize(maxPoolSize)
                .connectionFactoryConfiguration(cf -> cf
                    .jdbcUrl(jdbcUrl)
                    .connectionProviderClassName("org.sqlite.JDBC")
                    .autoCommit(true)));

    try {
      return AgroalDataSource.from(configuration);
    } catch (SQLException e) {
      throw new RuntimeException("Unable to open SQLite at path " + path, e);
    }
  }

  private static File initDbFile(Config config, String instanceId, String identifier) {
    File instanceDbsDir = getInstanceDbsDir(config, instanceId);
    File dbFile = new File(instanceDbsDir, identifier + ".db");

    try {
      FileUtils.forceMkdirParent(dbFile);
      return dbFile;
    } catch (IOException ex) {
      throw new RuntimeException("Cannot create SQLite directory for " + dbFile, ex);
    }
  }

  private static File getInstanceDbsDir(Config config, String instanceId) {
    String rootDir = config
        .getOptionalValue(PropertyNames.SQLITE_PATH, String.class)
        .orElse(DEFAULT_SQLITE_PATH);

    return new File(rootDir, instanceId);
  }

  @PreDestroy
  public void closeAll() {
    dataSourceMap.values().forEach(AgroalDataSource::close);
  }
}