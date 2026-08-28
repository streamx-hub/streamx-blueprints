package com.streamx.blueprints.state.sql;

import com.streamx.blueprints.state.sql.repository.PropertyNames;
import com.streamx.blueprints.state.sql.repository.SqlRepository;
import com.streamx.blueprints.state.sql.repository.sqlite.SqliteManager;
import com.streamx.blueprints.state.sql.repository.sqlite.SqliteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class SqlRepositoryFactory {

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9-.]+$");
  private static final String IDENTIFIER_PATTERN_DESCRIPTION =
      "only letters, digits, dashes and dots allowed";

  @Inject
  SqliteManager sqliteManager;

  public SqlRepository getOrCreate(String identifier) {
    Config config = ConfigProvider.getConfig();
    String backend = config.getOptionalValue(PropertyNames.BACKEND, String.class)
        .orElse(SqliteRepository.BACKEND);
    String instanceId = config.getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
        .orElse("unnamed");

    validateIdentifier(instanceId, "instanceId");
    validateIdentifier(identifier, "identifier");

    if (backend.equals(SqliteRepository.BACKEND)) {
      return new SqliteRepository(
          sqliteManager.getOrCreateDb(config, instanceId, identifier));
    }

    throw new UnsupportedOperationException("No SqlRepository for backend " + backend);
  }

  private static void validateIdentifier(String identifier, String fieldName) {
    if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
          "Invalid " + fieldName + ": " + identifier + " - " + IDENTIFIER_PATTERN_DESCRIPTION);
    }
  }
}
