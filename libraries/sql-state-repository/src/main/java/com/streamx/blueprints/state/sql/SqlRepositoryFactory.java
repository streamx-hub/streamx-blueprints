package com.streamx.blueprints.state.sql;

import com.streamx.blueprints.state.sql.repository.SqlRepository;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class SqlRepositoryFactory {

  @All
  @Inject
  List<SqlRepository> repositories;

  public SqlRepository get(String identifier) {
    return repositories.stream()
        .filter(repository -> repository.getIdentifier().equals(identifier))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot find repository with identifier=" + identifier));
  }
}
