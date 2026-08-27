package com.streamx.blueprints.sql;

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
import static com.streamx.blueprints.sql.utils.SerializationUtils.deserializeValue;
import static com.streamx.blueprints.sql.utils.SerializationUtils.serializeValue;
import static java.util.Objects.requireNonNull;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.sql.configuration.Configuration;
import com.streamx.blueprints.state.sql.SqlRepositoryFactory;
import com.streamx.blueprints.state.sql.repository.SqlRepository;
import io.cloudevents.CloudEvent;
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
public class StateRepository {

  @Inject
  Configuration configuration;

  @Inject
  SqlRepositoryFactory repositoryFactory;

  SqlRepository repository;

  @PostConstruct
  void init() {
    repository = repositoryFactory.getOrCreate("indexable-resources");

    repository.executeQuery(CREATE_INDEXABLE_RESOURCE);
    repository.executeQuery(CREATE_INDEXABLE_RESOURCE_FACETS);
    repository.executeQuery(CREATE_INDEXABLE_RESOURCE_FIELDS);
  }

  public List<ResourceEntity> read(String sqlQuery) {
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
      IndexableResourceContent content = deserializeValue(
          indexableResource.getContentAsString(),
          IndexableResourceContent.class);
      ResourceEntity resourceEntity = includeOnlyConfiguredPersistedData(
          new ResourceEntity(subject, content.title(),
              content.content(), content.facets(), content.fields()), configuration);
      save(resourceEntity);
    } else if (IndexableResource.TYPE_UNPUBLISHED.equals(eventType)) {
      delete(subject);
    }
  }

  public void save(ResourceEntity resource) {
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

  private void insertResource(Connection connection, ResourceEntity resource)
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

  private Map<String, Object> loadProperties(String subject, String sql) {
    return repository.query(sql, resultSet ->
        Map.entry(resultSet.getString("key"),
            deserializeValue(resultSet.getString("value"), Object.class)), subject
    ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private ResourceEntity mapResource(ResultSet resultSet) throws SQLException {
    String subject = resultSet.getString("subject");

    return new ResourceEntity(
        subject,
        resultSet.getString("title"),
        resultSet.getString("content"),
        loadProperties(subject, SELECT_FACETS),
        loadProperties(subject, SELECT_FIELDS)
    );

  }

  private ResourceEntity includeOnlyConfiguredPersistedData(ResourceEntity resource,
      Configuration configuration) {
    List<String> configuredFacets =
        configuration.persistedData().facets().orElseGet(List::of);

    List<String> configuredFields =
        configuration.persistedData().fields().orElseGet(List::of);

    return new ResourceEntity(
        resource.subject(),
        resource.title(),
        configuration.persistedData().includeContent() ? resource.content() : "",
        resource.facets().entrySet().stream()
            .filter(entry -> configuredFacets.contains(entry.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            )),
        resource.fields().entrySet().stream()
            .filter(entry -> configuredFields.contains(entry.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ))
    );
  }
}

