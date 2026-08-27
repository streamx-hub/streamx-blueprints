package com.streamx.blueprints.state.sql.repository.sqlite;

import com.streamx.blueprints.state.sql.repository.AbstractSqlRepository;
import java.sql.Connection;

public class SqliteRepository extends AbstractSqlRepository {

  public static final String BACKEND = "sqlite";

  public SqliteRepository(Connection connection) {
    super(connection);
  }
}
