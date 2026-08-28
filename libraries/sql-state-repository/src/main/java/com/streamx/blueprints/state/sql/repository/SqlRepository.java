package com.streamx.blueprints.state.sql.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface SqlRepository {

  void executeQuery(String sqlQuery);

  <T> T transaction(SqlTransaction<T> transaction);

  <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters);

  @FunctionalInterface
  interface SqlTransaction<T> {

    T execute(Connection connection) throws Exception;
  }

  @FunctionalInterface
  interface RowMapper<T> {

    T map(ResultSet resultSet) throws SQLException;
  }

}
