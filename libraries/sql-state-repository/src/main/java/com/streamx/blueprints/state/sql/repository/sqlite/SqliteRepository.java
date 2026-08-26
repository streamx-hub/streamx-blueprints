package com.streamx.blueprints.state.sql.repository.sqlite;

import com.streamx.blueprints.state.sql.repository.impl.AbstractSqlRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SqliteRepository extends AbstractSqlRepository {

  @Override
  public String getIdentifier() {
    return "sqlite";
  }
}
