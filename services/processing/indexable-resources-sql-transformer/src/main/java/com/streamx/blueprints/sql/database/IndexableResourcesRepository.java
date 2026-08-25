package com.streamx.blueprints.sql.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IndexableResourcesRepository {

  private static final String SQL = """
      INSERT INTO indexable_resources
          (subject, title, url, description, publication_date, modification_date, tags, author,
          image, language, content_type, metadata)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  @Inject
  protected Logger log;

  public IndexableResourcesRepository(
      DataSource dataSource,
      ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
  }

  public void save(IndexableSqlResources resource) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(SQL)) {

      resource.toStatement(statement)
          .executeUpdate();
      log.debugf("Saved resource with title=%s", resource.getTitle());
    } catch (SQLException e) {
      throw new RuntimeException("Issue during repository save operation", e);
    }
  }

  public List<IndexableSqlResources> read(String sqlQuery) {
    List<IndexableSqlResources> resources = new ArrayList<>();

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          resources.add(IndexableSqlResources.toEntity(rs));
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("Issue during repository read operation", e);
    }
    return resources;
  }
}
