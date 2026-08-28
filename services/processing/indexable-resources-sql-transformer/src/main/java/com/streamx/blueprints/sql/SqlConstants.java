package com.streamx.blueprints.sql;

public class SqlConstants {

  private SqlConstants() {
  }

  public static final String CREATE_INDEXABLE_RESOURCE = """
      CREATE TABLE IF NOT EXISTS indexable_resource (
          subject TEXT PRIMARY KEY,
          title TEXT,
          content TEXT
      )
      """;

  public static final String CREATE_INDEXABLE_RESOURCE_FACETS = """
      CREATE TABLE IF NOT EXISTS indexable_resource_facets (
          resource_subject TEXT NOT NULL,
          key TEXT NOT NULL,
          value TEXT,
          PRIMARY KEY (resource_subject, key),
          FOREIGN KEY (resource_subject)
              REFERENCES indexable_resource(subject)
              ON DELETE CASCADE
      )
      """;

  public static final String CREATE_INDEXABLE_RESOURCE_FIELDS = """
      CREATE TABLE IF NOT EXISTS indexable_resource_fields (
          resource_subject TEXT NOT NULL,
          key TEXT NOT NULL,
          value TEXT,
          PRIMARY KEY (resource_subject, key),
          FOREIGN KEY (resource_subject)
              REFERENCES indexable_resource(subject)
              ON DELETE CASCADE
      )
      """;

  public static final String INSERT_RESOURCE = """
      INSERT INTO indexable_resource (
          subject,
          title,
          content
      ) VALUES (?, ?, ?)
      ON CONFLICT(subject)
      DO UPDATE SET
          title = excluded.title,
          content = excluded.content
      """;

  public static final String INSERT_FACET = """
      INSERT INTO indexable_resource_facets (
          resource_subject,
          key,
          value
      ) VALUES (?, ?, ?)
      ON CONFLICT(resource_subject, key)
      DO UPDATE SET
          value = excluded.value
      """;

  public static final String INSERT_FIELD = """
      INSERT INTO indexable_resource_fields (
          resource_subject,
          key,
          value
      ) VALUES (?, ?, ?)
      ON CONFLICT(resource_subject, key)
      DO UPDATE SET
          value = excluded.value
      """;


  public static final String DELETE_RESOURCE = """
      DELETE FROM indexable_resource
      WHERE subject = ?
      """;

  public static final String DELETE_FACETS_BY_SUBJECT = """
      DELETE FROM indexable_resource_facets
      WHERE resource_subject = ?
      """;

  public static final String DELETE_FIELDS_BY_SUBJECT = """
      DELETE FROM indexable_resource_fields
      WHERE resource_subject = ?
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
