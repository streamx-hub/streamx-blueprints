package com.streamx.blueprints.sql.repository;

import static com.streamx.blueprints.sql.Channels.INDEXABLE_RESORUCES_STATE;
import static com.streamx.blueprints.sql.SqlConstants.CREATE_INDEXABLE_RESOURCE;
import static com.streamx.blueprints.sql.SqlConstants.CREATE_INDEXABLE_RESOURCE_FACETS;
import static com.streamx.blueprints.sql.SqlConstants.CREATE_INDEXABLE_RESOURCE_FIELDS;
import static com.streamx.blueprints.sql.SqlConstants.DELETE_RESOURCE;
import static com.streamx.blueprints.sql.SqlConstants.INSERT_FACET;
import static com.streamx.blueprints.sql.SqlConstants.INSERT_FIELD;
import static com.streamx.blueprints.sql.SqlConstants.INSERT_RESOURCE;
import static com.streamx.blueprints.sql.SqlConstants.SELECT_FACETS;
import static com.streamx.blueprints.sql.SqlConstants.SELECT_FIELDS;
import static io.smallrye.config._private.ConfigLogging.log;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.sql.IndexableResourceContent;
import com.streamx.blueprints.sql.NormalizedResource;
import com.streamx.blueprints.sql.configuration.Configuration;
import com.streamx.blueprints.state.sql.SqlRepositoryFactory;
import com.streamx.blueprints.state.sql.repository.SqlRepository;
import io.cloudevents.CloudEvent;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class IndexableResourcesRepository {

  SqlRepository repository;

  @Inject
  Configuration configuration;

  @Inject
  SqlRepositoryFactory repositoryFactory;

  static final ObjectMapper objectMapper = new ObjectMapper().configure(
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false
  );

  @PostConstruct
  void init() {
    repository = repositoryFactory.get("sqlite");

    repository.executeQuery(CREATE_INDEXABLE_RESOURCE);
    repository.executeQuery(CREATE_INDEXABLE_RESOURCE_FACETS);
    repository.executeQuery(CREATE_INDEXABLE_RESOURCE_FIELDS);
  }

  public List<NormalizedResource> read(String sqlQuery) {
    return repository.query(sqlQuery, this::mapResource
    );
  }

  @Incoming(INDEXABLE_RESORUCES_STATE)
  public void save(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    if (IndexableResource.TYPE_PUBLISHED.equals(eventType)) {
      IndexableResource indexableResource = requireNonNull(
          CloudEventUtils.getData(event, IndexableResource.class));
      try {
        IndexableResourceContent content = objectMapper.readValue(
            indexableResource.getContentAsString(),
            IndexableResourceContent.class);
        NormalizedResource normalizedResource = filterNormalizedResource(
            new NormalizedResource(subject, content.title(),
                content.content(), content.facets(), content.fields()));
        save(normalizedResource);
      } catch (JsonProcessingException e) {
        log.warn("Failed to parse resource");
      }

    } else if (IndexableResource.TYPE_UNPUBLISHED.equals(eventType)) {
      delete(subject);
    }
  }

  public void save(NormalizedResource resource) {
    repository.transaction(connection -> {
      insertResource(connection, resource);
      insertProperties(connection, resource.subject(), resource.facets(), INSERT_FACET);
      insertProperties(connection, resource.subject(), resource.fields(), INSERT_FIELD);

      return null;
    });
  }

  public void delete(String subject) {
    repository.transaction(connection -> {
      deleteResource(connection, subject);
      return null;
    });
  }

  private void insertResource(Connection connection, NormalizedResource resource)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(INSERT_RESOURCE)) {
      statement.setString(1, resource.subject());
      statement.setString(2, resource.title());
      statement.setString(3, resource.content());

      statement.executeUpdate();
    }
  }

  private void insertProperties(Connection connection, String subject,
      Map<String, Object> properties, String sql) throws SQLException {
    if (properties == null || properties.isEmpty()) {
      return;
    }
    try (PreparedStatement statement =
        connection.prepareStatement(sql)) {
      for (Map.Entry<String, Object> entry : properties.entrySet()) {
        statement.setString(1, subject);
        statement.setString(2, entry.getKey());
        statement.setString(3, serializeValue(entry.getValue()));
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private void deleteResource(Connection connection, String subject) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(DELETE_RESOURCE)) {
      statement.setString(1, subject);
      statement.executeUpdate();
    }
  }

  private NormalizedResource filterNormalizedResource(NormalizedResource resource) {
    return new NormalizedResource(
        resource.subject(),
        resource.title(),
        resource.content(),
        resource.facets().entrySet().stream()
            .filter(entry -> configuration.persistedData().facets().contains(entry.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            )),
        resource.fields().entrySet().stream()
            .filter(entry -> configuration.persistedData().fields().contains(entry.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ))
    );
  }

  private String serializeValue(Object value) {
    if (value == null) {
      return null;
    }

    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize value to JSON", e);
    }
  }

  private NormalizedResource mapResource(ResultSet resultSet) throws SQLException {
    String subject = resultSet.getString("subject");

    return new NormalizedResource(
        subject,
        resultSet.getString("title"),
        resultSet.getString("content"),
        loadProperties(subject, SELECT_FACETS),
        loadProperties(subject, SELECT_FIELDS)
    );
  }

  private Map<String, Object> loadProperties(String subject, String sql) {
    return repository.query(sql, resultSet ->
        Map.entry(resultSet.getString("key"),
            deserializeValue(resultSet.getString("value"))), subject
    ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Object deserializeValue(String value) {
    if (value == null) {
      return null;
    }

    try {
      return objectMapper.readValue(value, Object.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to deserialize value from JSON: " + value,
          e
      );
    }
  }

}

