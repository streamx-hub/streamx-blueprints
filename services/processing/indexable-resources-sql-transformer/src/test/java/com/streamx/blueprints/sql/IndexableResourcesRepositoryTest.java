package com.streamx.blueprints.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.sql.database.DatabaseInitializer;
import com.streamx.blueprints.sql.database.IndexableResourcesRepository;
import com.streamx.blueprints.sql.database.IndexableSqlResources;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class IndexableResourcesRepositoryTest {

  @Inject
  IndexableResourcesRepository repository;
  @Inject
  DatabaseInitializer databaseInitializer;
  @Inject
  DataSource dataSource;

  @BeforeEach
  void cleanDatabase() throws SQLException {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      stmt.executeUpdate("DELETE FROM indexable_resources");
    }
  }

  @Test
  void shouldWriteNormalizedDataToDatabase() {
    // given
    String sqlQuery = "SELECT * FROM indexable_resources";
    var resource = new IndexableSqlResources(
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

    // when
    repository.save(resource);

    // then
    List<IndexableSqlResources> result = repository.read(sqlQuery);
    assertThat(result).hasSize(1);
    assertThat(result.getFirst())
        .usingRecursiveComparison()
        .isEqualTo(resource);
  }
}
