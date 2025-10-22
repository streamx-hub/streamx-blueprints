package com.streamx.blueprints.opensearch;

import com.streamx.blueprints.opensearch.sink.opensearch.StartupService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class StartupServiceProxy extends StartupService {

  @Override
  protected void setup(@Observes @Priority(PRIORITY) StartupEvent event) {
    if (DockerUtils.isDockerAvailable) {
      migrate();
    }
  }
}
