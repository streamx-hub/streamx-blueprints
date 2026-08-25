package com.streamx.blueprints.sql.database;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

@Startup
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
                    url TEXT,
                    description TEXT,
                    publication_date TEXT,
                    modification_date TEXT,
                    tags TEXT,
                    author TEXT,
                    image TEXT,
                    language TEXT,
                    content_type TEXT,
                    metadata TEXT
                )
          """);
    }
  }
}
