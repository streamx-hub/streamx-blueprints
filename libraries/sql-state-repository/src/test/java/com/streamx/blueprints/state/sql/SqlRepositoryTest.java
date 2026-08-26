package com.streamx.blueprints.state.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamx.blueprints.state.sql.repository.SqlRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteException;

@QuarkusTest
public class SqlRepositoryTest {

  private static final String CREATE_TABLE_QUERY = """
      
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
      """;

  private static final String INSERT_QUERY = """
      INSERT INTO indexable_resources
          (subject, title, url, description, publication_date, modification_date, tags, author,
          image, language, content_type, metadata)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  @Inject
  SqlRepositoryFactory repositoryFactory;
  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanDatabase() {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.executeUpdate("DELETE FROM indexable_resources");
    } catch (SQLException e) {
      // No table
    }
  }

  @Test
  void shouldWriteNormalizedDataToDatabase() {
    // given
    String sqlQuery = "SELECT * FROM indexable_resources";
    IndexableSqlResources resource = new IndexableSqlResources(
        "subject-1",
        "My title",
        "https://example.com",
        "Description",
        "2025-07-15",
        null,
        "tag1,tag2",
        "David Beckham",
        "https://example.com/image.png",
        "en",
        "text/html",
        "{}"
    );
    SqlRepository repository = repositoryFactory.get("sqlite");
    repository.executeQuery(CREATE_TABLE_QUERY);
    IndexableSqResourcesMapper mapper = new IndexableSqResourcesMapper();

    // when
    repository.save(INSERT_QUERY, mapper, resource);

    // then
    List<IndexableSqlResources> result = repository.read(sqlQuery, mapper);
    assertThat(result).hasSize(1);
    assertThat(result.getFirst())
        .usingRecursiveComparison()
        .isEqualTo(resource);
  }

  @Test
  void shouldThrowExceptionWhenDatabaseNotFound() {
    assertThatThrownBy(() -> repositoryFactory.get("test"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot find repository with identifier=test");
  }

  @Test
  void shouldThrowExceptionWhenQueryIsInvalid() {
    SqlRepository repository = repositoryFactory.get("sqlite");
    assertThatThrownBy(
        () -> repository.executeQuery("INSERT INTO no_table (Subject) VALUES (test)"))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(SQLiteException.class);
  }

  @Test
  void shouldThrowExceptionWhenWhenCannotReadFromDatabase() {
    SqlRepository repository = repositoryFactory.get("sqlite");
    assertThatThrownBy(
        () -> repository.read("SELECT * FROM no_table", new IndexableSqResourcesMapper()))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(SQLiteException.class)
        .hasMessage("Issue during repository read operation");
  }
}
