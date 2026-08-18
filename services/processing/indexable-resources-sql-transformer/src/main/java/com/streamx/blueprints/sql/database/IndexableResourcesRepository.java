package com.streamx.blueprints.sql.database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

@ApplicationScoped
public class IndexableResourcesRepository {

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;

  public IndexableResourcesRepository(
      DataSource dataSource,
      ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
  }

  public void save(IndexableSqlResources resource) throws SQLException, JsonProcessingException {
    String sql = """
        INSERT INTO indexable_resources
            (subject, title, content, facets, fields)
        VALUES (?, ?, ?, ?, ?)
        """;

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {

      statement.setString(1, resource.subject());
      statement.setString(2, resource.title());
      statement.setString(3, resource.content());
      statement.setString(4, objectMapper.writeValueAsString(resource.facets()));
      statement.setString(5, objectMapper.writeValueAsString(resource.fields()));

      statement.executeUpdate();
    }
  }

  public Object read(String sqlQuery) throws SQLException, JsonProcessingException {
    List<IndexableSqlResources> resources = new ArrayList<>();

    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sqlQuery)) {
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          String subject = rs.getString("subject");
          String title = rs.getString("title");
          String content = rs.getString("content");
          String facets = rs.getString("facets");
          String fields = rs.getString("fields");

          resources.add(new IndexableSqlResources(
              subject,
              title,
              content,
              objectMapper.readValue(facets,
                  new TypeReference<Map<String, Object>>() {
                  }),
              objectMapper.readValue(fields,
                  new TypeReference<Map<String, Object>>() {
                  })
          ));
        }
        return null;
      }
    }
  }
}
