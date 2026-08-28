package com.streamx.blueprints.state.sql.repository.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteManagerTest {

  @TempDir
  Path tempDir;

  private final SqliteManager manager = new SqliteManager();

  @AfterEach
  void tearDown() {
    manager.closeAll();
  }

  @Test
  void shouldCreateDatabaseUsingConfiguredPath() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource dataSource = manager.getOrCreateDb(config, "instance-1", "test");

    assertNotNull(dataSource);

    Path expectedPath = tempDir.resolve("instance-1").resolve("test.db");
    assertTrue(expectedPath.toFile().exists());

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE test (id INTEGER)");
    }
  }

  @Test
  void shouldEnableForeignKeysOnConnectionsFromPool() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource dataSource = manager.getOrCreateDb(config, "instance-1", "fk-test");

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt(1));
    }
  }

  @Test
  void shouldEnableWalJournalModeOnConnectionsFromPool() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource dataSource = manager.getOrCreateDb(config, "instance-1", "wal-test");

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("PRAGMA journal_mode")) {
      assertTrue(resultSet.next());
      assertEquals("wal", resultSet.getString(1).toLowerCase());
    }
  }

  @Test
  void shouldServeIndependentConnectionsForConcurrentUse() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource dataSource = manager.getOrCreateDb(config, "instance-1", "pool-test");

    try (Connection first = dataSource.getConnection();
        Connection second = dataSource.getConnection()) {
      assertNotNull(first);
      assertNotNull(second);
      assertFalse(first == second);
      assertFalse(first.isClosed());
      assertFalse(second.isClosed());
    }
  }

  @Test
  void shouldUseDefaultPathWhenConfigurationIsMissing() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.empty());

    DataSource dataSource = manager.getOrCreateDb(config, "test-instance", "test");

    assertNotNull(dataSource);

    assertTrue(
        Path.of("/tmp/sqlite")
            .resolve("test-instance")
            .resolve("test.db")
            .toFile()
            .exists());
  }

  @Test
  void shouldReturnSameDataSourceForSameDatabase() {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource first = manager.getOrCreateDb(config, "instance-1", "test");
    DataSource second = manager.getOrCreateDb(config, "instance-1", "test");

    assertSame(first, second);
  }

  @Test
  void shouldCreateDifferentDataSourcesForDifferentDatabases() {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource first = manager.getOrCreateDb(config, "instance-1", "database-1");
    DataSource second = manager.getOrCreateDb(config, "instance-1", "database-2");

    assertNotNull(first);
    assertNotNull(second);
    assertFalse(first == second);
  }

  @Test
  void shouldCreateDifferentDataSourcesForDifferentInstances() {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource first = manager.getOrCreateDb(config, "instance-1", "database");
    DataSource second = manager.getOrCreateDb(config, "instance-2", "database");

    assertNotNull(first);
    assertNotNull(second);
    assertFalse(first == second);
  }

  @Test
  void shouldThrowWhenDatabaseDirectoryCannotBeCreated() throws Exception {
    Config config = mock(Config.class);

    File pathAsFile = new File(tempDir.toFile(), "not-a-directory");
    assertTrue(pathAsFile.createNewFile());

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(pathAsFile.getAbsolutePath()));

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> manager.getOrCreateDb(config, "instance-1", "my-identifier"));

    assertTrue(exception.getMessage().startsWith("Cannot create SQLite directory"));
  }

  @Test
  void shouldCloseAllDataSources() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(PropertyNames.SQLITE_PATH, String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    DataSource first = manager.getOrCreateDb(config, "instance-1", "database-1");
    DataSource second = manager.getOrCreateDb(config, "instance-1", "database-2");

    manager.closeAll();

    assertTrue(((HikariDataSource) first).isClosed());
    assertTrue(((HikariDataSource) second).isClosed());
  }
}