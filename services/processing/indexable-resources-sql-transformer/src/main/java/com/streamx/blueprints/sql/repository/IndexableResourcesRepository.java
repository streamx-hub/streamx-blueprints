package com.streamx.blueprints.sql.repository;

import static com.streamx.blueprints.sql.repository.IndexableSqResourcesMapper.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.streamx.blueprints.state.sql.SqlRepositoryFactory;
import com.streamx.blueprints.state.sql.repository.SqlRepository;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class IndexableResourcesRepository {

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

  private SqlRepository repository;
  private IndexableSqResourcesMapper mapper = new IndexableSqResourcesMapper();

  @Inject
  SqlRepositoryFactory repositoryFactory;

  @PostConstruct
  void init() {
    repository = repositoryFactory.get("sqlite");
    repository.executeQuery(CREATE_TABLE_QUERY);
  }

  public List<IndexableSqlResources> read(String sqlQuery) {
    return repository.read(sqlQuery, mapper);
  }

  public void save(String subject, String title,
      Map<String, Object> fields) {
    try {
      repository.save(INSERT_QUERY, mapper, map(subject, title, fields));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Payload could not be serialized.", e);
    }
  }

  public void executeQuery(String sqlQuery) {
    repository.executeQuery(sqlQuery);
  }
}
