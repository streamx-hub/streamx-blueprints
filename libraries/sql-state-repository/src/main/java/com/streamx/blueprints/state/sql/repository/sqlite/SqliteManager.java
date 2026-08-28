package com.streamx.blueprints.state.sql.repository.sqlite;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;
import org.sqlite.SQLiteConfig;

@ApplicationScoped
public class SqliteManager {

  private static final String DEFAULT_SQLITE_PATH = "/tmp/sqlite";
  private static final int MAX_POOL_SIZE = 4;
  private static final int BUSY_TIMEOUT_MS = 5_000;

  private final Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();

  public DataSource getOrCreateDb(Config config, String instanceId, String identifier) {
    File dbFile = initDbFile(config, instanceId, identifier);
    String dbPath = dbFile.toPath().toAbsolutePath().normalize().toString();

    return dataSourceMap.computeIfAbsent(dbPath, this::createDataSource);
  }

  private HikariDataSource createDataSource(String path) {
    SQLiteConfig sqliteConfig = new SQLiteConfig();
    sqliteConfig.enforceForeignKeys(true);
    sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
    sqliteConfig.setBusyTimeout(BUSY_TIMEOUT_MS);

    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl("jdbc:sqlite:" + path);
    hikariConfig.setDataSourceProperties(sqliteConfig.toProperties());
    hikariConfig.setMaximumPoolSize(MAX_POOL_SIZE);
    hikariConfig.setPoolName("sqlite-" + path);
    hikariConfig.setConnectionInitSql("PRAGMA foreign_keys = ON");

    return new HikariDataSource(hikariConfig);
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
    dataSourceMap.values().forEach(HikariDataSource::close);
  }
}