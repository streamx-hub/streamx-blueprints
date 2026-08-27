package com.streamx.blueprints.state.sql.repository.sqlite;

import static io.smallrye.config._private.ConfigLogging.log;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class SqliteManager {

  private static final String DEFAULT_SQLITE_PATH = "/tmp/sqlite";

  private final Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

  public Connection getOrCreateDb(Config config, String instanceId, String identifier) {

    File dbFile = initDbFile(config, instanceId, identifier);
    String dbPath = dbFile.toPath()
        .toAbsolutePath()
        .normalize()
        .toString();

    return connectionMap.computeIfAbsent(dbPath, path -> {
      try {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
          statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
      } catch (SQLException e) {
        throw new RuntimeException(
            "Unable to open SQLite at path " + path, e);
      }
    });
  }

  private static File initDbFile(Config config, String instanceId, String identifier) {

    File instanceDbsDir = getInstanceDbsDir(config, instanceId);
    File dbFile = new File(instanceDbsDir, identifier + ".db");

    try {
      FileUtils.forceMkdirParent(dbFile);
      return dbFile;
    } catch (IOException ex) {
      throw new RuntimeException(
          "Cannot create SQLite directory for " + dbFile, ex);
    }
  }

  private static File getInstanceDbsDir(
      Config config,
      String instanceId) {

    String rootDir = config
        .getOptionalValue(PropertyNames.SQLITE_PATH, String.class)
        .orElse(DEFAULT_SQLITE_PATH);

    return new File(rootDir, instanceId);
  }

  @PreDestroy
  public void closeAll() {
    connectionMap.values().forEach(connection -> {
      try {
        connection.close();
      } catch (SQLException e) {
        log.warn("Failed to close connection", e);
      }
    });
  }
}
