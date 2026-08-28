package com.streamx.blueprints.state.sql;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import com.streamx.blueprints.state.sql.repository.sqlite.SqliteManager;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteManagerTest {

  @TempDir
  File tempDir;

  private SqliteManager sqliteManager;
  private Config config;

  @BeforeEach
  void setUp() {
    sqliteManager = new SqliteManager();
    config = ConfigProvider.getConfig();
    System.setProperty(PropertyNames.SQLITE_PATH, tempDir.getAbsolutePath());
  }

  @AfterEach
  void tearDown() {
    System.clearProperty(PropertyNames.SQLITE_PATH);
    sqliteManager.closeAll();
  }

  @Test
  void shouldEnableForeignKeysOnNewConnection() throws SQLException {
    Connection connection = sqliteManager.getOrCreateDb(config, "instance-1", "my-identifier");

    assertNotNull(connection);

    try (Statement statement = connection.createStatement()) {
      var resultSet = statement.executeQuery("PRAGMA foreign_keys");
      resultSet.next();
      assertSame(1, resultSet.getInt(1));
    }
  }

  @Test
  void shouldCreateDatabaseDirectoryAndFile() {
    Connection connection =
        sqliteManager.getOrCreateDb(config, "instance-1", "my-identifier");

    assertNotNull(connection);

    File expectedDbFile =
        new File(tempDir, "instance-1/my-identifier.db");

    assertTrue(expectedDbFile.exists());
    assertTrue(expectedDbFile.isFile());
  }

  @Test
  void shouldThrowWhenDatabaseDirectoryCannotBeCreated() throws Exception {
    File pathAsFile = new File(tempDir, "not-a-directory");
    assertTrue(pathAsFile.createNewFile());

    System.setProperty(PropertyNames.SQLITE_PATH, pathAsFile.getAbsolutePath());

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> sqliteManager.getOrCreateDb(config, "instance-1", "my-identifier"));

    assertTrue(exception.getMessage().startsWith("Cannot create SQLite directory"));
  }


  @Test
  void shouldReuseSameConnectionForSameIdentifier() {
    Connection first = sqliteManager.getOrCreateDb(config, "instance-1", "same-id");
    Connection second = sqliteManager.getOrCreateDb(config, "instance-1", "same-id");

    assertSame(first, second);
  }
}