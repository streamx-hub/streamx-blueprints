package com.streamx.blueprints.opensearch.sink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senacor.elasticsearch.evolution.core.ElasticsearchEvolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.elasticsearch.client.RestClient;

@Dependent
public class OpensearchConfig {

  public static final String PN_MIGRATION_SCRIPTS_LOCATIONS =
      "streamx.blueprints.opensearch-sink.migration-script-locations";

  @ConfigProperty(name = PN_MIGRATION_SCRIPTS_LOCATIONS,
      defaultValue = "classpath:opensearch/service-init"
  )
  List<String> migrationScriptsLocation;

  @ApplicationScoped
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @ApplicationScoped
  ElasticsearchEvolution elasticsearchEvolution(RestClient client) {
    return ElasticsearchEvolution.configure()
        .setLocations(migrationScriptsLocation)
        .load(client);
  }
}
