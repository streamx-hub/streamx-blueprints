package com.streamx.blueprints.opensearch.sink;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;

import com.streamx.blueprints.opensearch.sink.config.OpensearchConfig;
import com.streamx.blueprints.opensearch.sink.opensearch.DefaultRepository;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class BaseOpensearchTest {

  private static final AtomicBoolean migrationsExecuted = new AtomicBoolean(false);

  @Inject
  OpensearchConfig opensearchConfig;

  @InjectSpy
  DefaultRepository defaultRepository;

  @BeforeAll
  static void startOpensearchContainerIfDockerIsAvailable() {
    assumeTrue(
        DockerUtils.isDockerAvailable,
        "Skipping test, not able to run opensearch docker container"
    );
    OpensearchTestContainer.start();
  }

  @BeforeEach
  void runMigrations() {
    if (!migrationsExecuted.get()) {
      RestClient client = restClient();
      var elasticsearchEvolution = opensearchConfig.elasticsearchEvolution(client);
      elasticsearchEvolution.migrate();

      // run migration again, to verify executed scripts are skipped with no errors
      elasticsearchEvolution.migrate();

      migrationsExecuted.set(true);
    }
  }

  @BeforeEach
  void configureDefaultRepository() {
    doReturn(restClient()).when(defaultRepository).getClient();
  }

  private RestClient restClient() {
    HttpHost httpHost = new HttpHost(
        OpensearchTestContainer.getHost(),
        OpensearchTestContainer.getPort(),
        "http"
    );
    return RestClient.builder(httpHost).build();
  }
}
