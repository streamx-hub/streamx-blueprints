package com.streamx.blueprints.sql;

public class SqlConstants {

  private SqlConstants() {
  }

  public static final String CREATE_INDEXABLE_RESOURCE = """
      CREATE TABLE indexable_resource (
          subject TEXT PRIMARY KEY,
          title TEXT,
          content TEXT
      )
      """;

  public static final String CREATE_INDEXABLE_RESOURCE_FACETS = """
      CREATE TABLE indexable_resource_facets (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          resource_subject TEXT NOT NULL,
          key TEXT NOT NULL,
          value TEXT,
          FOREIGN KEY (resource_subject)
              REFERENCES indexable_resource(subject)
              ON DELETE CASCADE,
          UNIQUE (resource_subject, key)
      )
      """;

  public static final String CREATE_INDEXABLE_RESOURCE_FIELDS = """
      CREATE TABLE indexable_resource_fields (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          resource_subject TEXT NOT NULL,
          key TEXT NOT NULL,
          value TEXT,
          FOREIGN KEY (resource_subject)
              REFERENCES indexable_resource(subject)
              ON DELETE CASCADE,
          UNIQUE (resource_subject, key)
      )
      """;

  public static final String INSERT_RESOURCE = """
      INSERT INTO indexable_resource (
          subject,
          title,
          content
      ) VALUES (?, ?, ?)
      """;

  public static final String INSERT_FACET = """
      INSERT INTO indexable_resource_facets (
          resource_subject,
          key,
          value
      ) VALUES (?, ?, ?)
      """;

  public static final String INSERT_FIELD = """
      INSERT INTO indexable_resource_fields (
          resource_subject,
          key,
          value
      ) VALUES (?, ?, ?)
      """;

  public static final String DELETE_RESOURCE = """
      DELETE FROM indexable_resource
      WHERE subject = ?
      """;

  public static final String SELECT_FACETS = """
      SELECT key, value
      FROM indexable_resource_facets
      WHERE resource_subject = ?
      """;

  public static final String SELECT_FIELDS = """
      SELECT key, value
      FROM indexable_resource_fields
      WHERE resource_subject = ?
      """;

}
