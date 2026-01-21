package com.streamx.blueprints.state;

import com.streamx.blueprints.state.repository.inmemory.InMemoryRepository;
import com.streamx.blueprints.state.repository.inmemory.InMemoryRepositoryManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbManager;
import com.streamx.blueprints.state.repository.rocksdb.RocksDbRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.rocksdb.RocksDB;

@ApplicationScoped
public class RepositoryFactory {

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z0-9-.]+$");
  private static final String IDENTIFIER_PATTERN_DESCRIPTION =
      "only letters, digits, dashes and dots allowed";

  @Inject
  RocksDbManager rocksDbManager;

  public <T> StateRepository<T> getOrCreate(String identifier, Class<T> valueClass) {
    Config config = ConfigProvider.getConfig();
    String backend = config.getOptionalValue(PropertyNames.BACKEND, String.class)
        .orElse(InMemoryRepository.BACKEND);
    String instanceId = config.getOptionalValue(PropertyNames.SERVICE_INSTANCE_ID, String.class)
        .orElse("unnamed");

    validateIdentifier(instanceId, "instanceId");
    validateIdentifier(identifier, "identifier");

    if (backend.equals(RocksDbRepository.BACKEND)) {
      RocksDB rocksDb = rocksDbManager.getOrCreateDb(config, instanceId, identifier);
      return new RocksDbRepository<>(rocksDb, valueClass);
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

