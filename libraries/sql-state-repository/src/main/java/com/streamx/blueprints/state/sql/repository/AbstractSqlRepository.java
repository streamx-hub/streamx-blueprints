package com.streamx.blueprints.state.sql.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public abstract class AbstractSqlRepository implements SqlRepository {

  protected final DataSource dataSource;

  protected AbstractSqlRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void executeQuery(String sqlQuery) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sqlQuery);
    } catch (SQLException e) {
      throw new RuntimeException("SQL execution failed", e);
    }
  }

  @Override
  public <T> List<T> query(String sql, RowMapper<T> mapper, Object... parameters) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }

      try (ResultSet resultSet = statement.executeQuery()) {
        List<T> result = new ArrayList<>();
        while (resultSet.next()) {
          result.add(mapper.map(resultSet));
        }
        return result;
      }
    } catch (Exception e) {
      throw new RuntimeException("Query failed", e);
    }
  }

  @Override
  public <T> T transaction(SqlTransaction<T> transaction) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        T result = transaction.execute(connection);
        connection.commit();
        return result;
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    } catch (Exception e) {
      throw new RuntimeException("Transaction failed", e);
    }
  }
}