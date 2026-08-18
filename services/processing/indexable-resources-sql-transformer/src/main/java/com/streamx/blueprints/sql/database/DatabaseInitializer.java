package com.streamx.blueprints.sql.database;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

@ApplicationScoped
public class DatabaseInitializer {

  private final DataSource dataSource;

  public DatabaseInitializer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @PostConstruct
  void init() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {

      statement.execute("""
          
          CREATE TABLE IF NOT EXISTS indexable_resources (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT,
                    facets TEXT,
                    fields TEXT
                )
          """);
    }
  }
}
