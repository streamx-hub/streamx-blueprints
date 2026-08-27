package com.streamx.blueprints.state.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import com.streamx.blueprints.state.sql.repository.SqlRepository;
import com.streamx.blueprints.state.sql.repository.sqlite.SqliteManager;
import com.streamx.blueprints.state.sql.repository.sqlite.SqliteRepository;
import java.sql.Connection;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class SqlRepositoryFactoryTest {

  private SqliteManager sqliteManager;
  private SqlRepositoryFactory factory;
  private Config config;
  private Connection connection;

  @BeforeEach
  void setUp() {
    sqliteManager = mock(SqliteManager.class);
    config = mock(Config.class);
    connection = mock(Connection.class);

    factory = new SqlRepositoryFactory();
    factory.sqliteManager = sqliteManager;
  }

  @Test
  void shouldCreateSqliteRepository() {
    String identifier = "test-db";
    String instanceId = "instance-1";

    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.of(SqliteRepository.BACKEND));

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.of(instanceId));

    when(sqliteManager.getOrCreateDb(
        config,
        instanceId,
        identifier))
        .thenReturn(connection);

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      SqlRepository result =
          factory.getOrCreate(identifier);

      assertNotNullSqliteRepository(result);

      verify(sqliteManager)
          .getOrCreateDb(
              config,
              instanceId,
              identifier);
    }
  }

  @Test
  void shouldUseDefaultBackendWhenBackendIsNotConfigured() {
    String identifier = "test-db";
    String instanceId = "instance-1";

    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.empty());

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.of(instanceId));

    when(sqliteManager.getOrCreateDb(
        config,
        instanceId,
        identifier))
        .thenReturn(connection);

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      SqlRepository result =
          factory.getOrCreate(identifier);

      assertNotNullSqliteRepository(result);

      verify(sqliteManager)
          .getOrCreateDb(
              config,
              instanceId,
              identifier);
    }
  }

  @Test
  void shouldUseDefaultInstanceIdWhenNotConfigured() {
    String identifier = "test-db";
    String defaultInstanceId = "unnamed";

    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.of(SqliteRepository.BACKEND));

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.empty());

    when(sqliteManager.getOrCreateDb(
        config,
        defaultInstanceId,
        identifier))
        .thenReturn(connection);

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      SqlRepository result =
          factory.getOrCreate(identifier);

      assertNotNullSqliteRepository(result);

      verify(sqliteManager)
          .getOrCreateDb(
              config,
              defaultInstanceId,
              identifier);
    }
  }

  @Test
  void shouldThrowExceptionForUnsupportedBackend() {
    String backend = "postgres";

    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.of(backend));

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.of("instance-1"));

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      UnsupportedOperationException exception =
          assertThrows(
              UnsupportedOperationException.class,
              () -> factory.getOrCreate("test-db"));

      assertEquals(
          "No SqlRepository for backend postgres",
          exception.getMessage());
    }
  }

  @Test
  void shouldRejectInvalidIdentifier() {
    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.of(SqliteRepository.BACKEND));

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.of("instance-1"));

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> factory.getOrCreate("invalid_identifier"));

      assertEquals(
          "Invalid identifier: invalid_identifier - "
              + "only letters, digits, dashes and dots allowed",
          exception.getMessage());
    }
  }

  @Test
  void shouldRejectInvalidInstanceId() {
    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.of(SqliteRepository.BACKEND));

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.of("invalid_instance"));

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> factory.getOrCreate("test-db"));

      assertEquals(
          "Invalid instanceId: invalid_instance - "
              + "only letters, digits, dashes and dots allowed",
          exception.getMessage());
    }
  }

  @Test
  void shouldAcceptValidIdentifier() {
    when(config.getOptionalValue(
        PropertyNames.BACKEND,
        String.class))
        .thenReturn(Optional.of(SqliteRepository.BACKEND));

    when(config.getOptionalValue(
        PropertyNames.SERVICE_INSTANCE_ID,
        String.class))
        .thenReturn(Optional.of("instance-1.test"));

    when(sqliteManager.getOrCreateDb(
        config,
        "instance-1.test",
        "my-db.test-123"))
        .thenReturn(connection);

    try (MockedStatic<ConfigProvider> configProvider =
        mockStatic(ConfigProvider.class)) {

      configProvider
          .when(ConfigProvider::getConfig)
          .thenReturn(config);

      SqlRepository result =
          factory.getOrCreate("my-db.test-123");

      assertNotNullSqliteRepository(result);

      verify(sqliteManager)
          .getOrCreateDb(
              config,
              "instance-1.test",
              "my-db.test-123");
    }
  }

  private void assertNotNullSqliteRepository(
      SqlRepository repository) {

    assertInstanceOf(
        SqliteRepository.class,
        repository);
  }
}
