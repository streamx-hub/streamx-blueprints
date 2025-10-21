package com.streamx.blueprints.opensearch.sink.opensearch;

import com.senacor.elasticsearch.evolution.core.ElasticsearchEvolution;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class StartupService {

  /**
   * Before SmallRye Reactive Messaging is initialized (1000),
   * but after OpenSearch client is initialized.
   */
  protected static final int PRIORITY = Interceptor.Priority.LIBRARY_BEFORE - 10;

  @ConfigProperty(name = "streamx.blueprints.opensearch-sink.migration-auto-run",
      defaultValue = "true")
  boolean autoRunMigration;

  @Inject
  ElasticsearchEvolution elasticsearchEvolution;

  protected void setup(@Observes @Priority(PRIORITY) StartupEvent event) {
    if (autoRunMigration) {
      migrate();
    }
  }

  protected void migrate() {
    elasticsearchEvolution.migrate();
  }
}
