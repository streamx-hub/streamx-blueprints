package com.streamx.blueprints.state;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import com.streamx.blueprints.state.repository.inmemory.InMemoryRepositoryManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;

public final class RepositoryFactory {

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9-.]+$");
  private static final String IDENTIFIER_PATTERN_DESCRIPTION =
      "only letters, digits, dashes and dots allowed";

  private RepositoryFactory() {
    // no instances
  }

  public static <T> StateRepository<T> createRepository(Config config, Class<T> valueClass,
      String identifier) {
    String backend = config.getOptionalValue(PropertyNames.STATE_BACKEND, String.class)
        .orElse(InMemoryRepository.BACKEND);
    String instanceId = config.getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
        .orElse("unnamed");

    validateIdentifier(instanceId, "instanceId");
    validateIdentifier(identifier, "identifier");

    if (backend.equals(RocksDbRepository.BACKEND)) {
      return new RocksDbRepository<>(config, valueClass, instanceId, identifier);
    }
    if (backend.equals(InMemoryRepository.BACKEND)) {
      return InMemoryRepositoryManager.getOrCreate(instanceId, identifier);
    }
    throw new UnsupportedOperationException("No StateRepository for backend " + backend);
  }

  private static void validateIdentifier(String identifier, String fieldName) {
    if (!IDENTIFIER_PATTERN.matcher(identifier).matches()) {
      throw new IllegalArgumentException(
          "Invalid " + fieldName + ": " + identifier + " - " + IDENTIFIER_PATTERN_DESCRIPTION);
    }
  }

}

