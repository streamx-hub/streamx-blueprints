package com.streamx.blueprints.state.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class IndexableSqResourcesMapper implements EntityMapper<IndexableSqlResources> {

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
}
