package com.streamx.blueprints.state.sql.repository.impl;

import com.streamx.blueprints.state.sql.EntityMapper;
import com.streamx.blueprints.state.sql.repository.SqlRepository;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.jboss.logging.Logger;

public abstract class AbstractSqlRepository implements SqlRepository {

  @Inject
  protected Logger log;
  @Inject
  protected DataSource dataSource;

  public void executeQuery(String sqlQuery) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {

      statement.execute(sqlQuery);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> void save(String sqlQuery, EntityMapper<T> mapper, T resource) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sqlQuery)) {

      mapper.toStatement(statement, resource)
          .executeUpdate();
      log.debug("Resource saved");
    } catch (SQLException e) {
      throw new RuntimeException("Issue during repository save operation", e);
    }
  }

  public <T> List<T> read(String sqlQuery, EntityMapper<T> mapper) {
    List<T> resources = new ArrayList<>();

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          resources.add(mapper.map(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Issue during repository read operation", e);
    }
    return resources;
  }
}
