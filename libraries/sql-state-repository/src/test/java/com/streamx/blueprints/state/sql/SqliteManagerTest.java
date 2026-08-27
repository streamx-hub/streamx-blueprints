package com.streamx.blueprints.state.sql.repository.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
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

    when(config.getOptionalValue(
        PropertyNames.SQLITE_PATH,
        String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    Connection connection =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "test");

    assertNotNull(connection);
    assertFalse(connection.isClosed());

    Path expectedPath =
        tempDir
            .resolve("instance-1")
            .resolve("test.db");

    assertTrue(
        expectedPath.toFile().exists());

    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE test (id INTEGER)");
    }
  }

  @Test
  void shouldUseDefaultPathWhenConfigurationIsMissing() throws Exception {
    Config config = mock(Config.class);

    when(config.getOptionalValue(
        PropertyNames.SQLITE_PATH,
        String.class))
        .thenReturn(Optional.empty());

    Connection connection =
        manager.getOrCreateDb(
            config,
            "test-instance",
            "test");

    assertNotNull(connection);
    assertFalse(connection.isClosed());

    assertTrue(
        Path.of("/tmp/sqlite")
            .resolve("test-instance")
            .resolve("test.db")
            .toFile()
            .exists());
  }

  @Test
  void shouldReturnSameConnectionForSameDatabase() {
    Config config = mock(Config.class);

    when(config.getOptionalValue(
        PropertyNames.SQLITE_PATH,
        String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    Connection first =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "test");

    Connection second =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "test");

    assertSame(first, second);
  }

  @Test
  void shouldCreateDifferentConnectionsForDifferentDatabases() {
    Config config = mock(Config.class);

    when(config.getOptionalValue(
        PropertyNames.SQLITE_PATH,
        String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    Connection first =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "database-1");

    Connection second =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "database-2");

    assertNotNull(first);
    assertNotNull(second);

    assertFalse(first == second);
  }

  @Test
  void shouldCreateDifferentConnectionsForDifferentInstances() {
    Config config = mock(Config.class);

    when(config.getOptionalValue(
        PropertyNames.SQLITE_PATH,
        String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    Connection first =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "database");

    Connection second =
        manager.getOrCreateDb(
            config,
            "instance-2",
            "database");

    assertNotNull(first);
    assertNotNull(second);

    assertFalse(first == second);
  }

  @Test
  void shouldCloseAllConnections() throws SQLException {
    Config config = mock(Config.class);

    when(config.getOptionalValue(
        PropertyNames.SQLITE_PATH,
        String.class))
        .thenReturn(Optional.of(tempDir.toString()));

    Connection first =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "database-1");

    Connection second =
        manager.getOrCreateDb(
            config,
            "instance-1",
            "database-2");

    manager.closeAll();

    assertTrue(first.isClosed());
    assertTrue(second.isClosed());
  }

}
