package com.streamx.blueprints.opensearch.sink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.senacor.elasticsearch.evolution.core.ElasticsearchEvolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.elasticsearch.client.RestClient;

@Dependent
public class OpensearchConfig {

  @Inject
  Configuration configuration;

  @ApplicationScoped
  ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }

  @ApplicationScoped
  public ElasticsearchEvolution elasticsearchEvolution(RestClient client) {
    return ElasticsearchEvolution.configure()
        .setLocations(configuration.migrationScriptLocations())
        .load(client);
  }
}
