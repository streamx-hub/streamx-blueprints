package com.streamx.blueprints.sql.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.state.sql.EntityMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class IndexableSqResourcesMapper implements EntityMapper<IndexableSqlResources> {

  static final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );

  @Override
  public PreparedStatement toStatement(PreparedStatement statement, IndexableSqlResources entity)
      throws SQLException {
    statement.setString(1, entity.subject());
    statement.setString(2, entity.title());
    statement.setString(3, entity.url());
    statement.setString(4, entity.description());
    statement.setString(5, entity.publicationDate());
    statement.setString(6, entity.modificationDate());
    statement.setString(7, entity.tags());
    statement.setString(8, entity.author());
    statement.setString(9, entity.image());
    statement.setString(10, entity.language());
    statement.setString(11, entity.contentType());
    statement.setString(12, entity.metadata());
    return statement;
  }

  @Override
  public IndexableSqlResources map(ResultSet rs) throws SQLException {
    return new IndexableSqlResources(
        rs.getString("subject"),
        rs.getString("title"),
        rs.getString("url"),
        rs.getString("description"),
        rs.getString("publication_date"),
        rs.getString("modification_date"),
        rs.getString("tags"),
        rs.getString("author"),
        rs.getString("image"),
        rs.getString("language"),
        rs.getString("content_type"),
        rs.getString("metadata")
    );
  }

  protected static IndexableSqlResources map(String subject, String title,
      Map<String, Object> fields)
      throws JsonProcessingException {
    return new IndexableSqlResources(subject,
        title,
        toString(fields.get("url")),
        toString(fields.get("description")),
        toString(fields.get("publication_date")),
        toString(fields.get("modification_date")),
        fields.containsKey("tags") ? objectMapper.writeValueAsString(fields.get("tags")) : null,
        toString(fields.get("author")),
        toString(fields.get("image")),
        toString(fields.get("language")),
        toString(fields.get("content_type")),
        fields.containsKey("metadata")
            ? objectMapper.writeValueAsString(fields.get("metadata"))
            : null);
  }

  private static String toString(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
