package com.streamx.blueprints.state.sql.repository.sqlite;

import com.streamx.blueprints.state.sql.repository.AbstractSqlRepository;
import javax.sql.DataSource;

public class SqliteRepository extends AbstractSqlRepository {

  public static final String BACKEND = "sqlite";

  public SqliteRepository(DataSource dataSource) {
    super(dataSource);
  }
}
