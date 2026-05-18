package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.functions.settings.BaseProcessingSettings;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessingContextResolver {

  @Inject
  Instance<BaseProcessingSettings<?>> processingSettings;

  @Inject
  Configuration configuration;

  @Inject
  Logger log;

  public Optional<ProcessingContext> resolveContext(CloudEvent event) {
    Resource payload = extractResource(event);
    if (payload == null) {
      return Optional.empty();
    }

    String resourcePath = CloudEventUtils.getSubject(event);
    String payloadType = payload.getType();

    if (!configuration.processablePayloadTypes().contains(payloadType)) {
      log.tracef("Skipping processing %s - unsupported payload type %s", resourcePath,
          payloadType
      );
      return Optional.empty();
    }

    Optional<BaseProcessingSettings<?>> settingsOpt = processingSettings.stream()
        .filter(setting -> setting.handledCloudEventType(event.getType()))
        .filter(setting -> setting.handlesResourcePath(resourcePath))
        .findFirst();

    if (settingsOpt.isEmpty()) {
      log.tracef("No handler for resource event %s of type %s", resourcePath,
          event.getType()
      );
      return Optional.empty();
    }

    BaseProcessingSettings<?> settings = settingsOpt.get();

    if (!settings.getExternalResourcesCollector().hasResourceSelectors()) {
      log.tracef("Skipping processing %s - no resource selectors specified", resourcePath);
      return Optional.empty();
    }

    return Optional.of(new ProcessingContext(payload, payloadType, resourcePath, settings));
  }

  private Resource extractResource(CloudEvent event) {
    try {
      Resource resource = CloudEventUtils.getData(event, Resource.class);
      if (resource == null) {
        log.tracef("Skipping processing %s - payload is null - cannot determine payload type",
            event.getSubject());
      }
      return resource;
    } catch (RuntimeException ex) {
      log.warnf("Invalid incoming CloudEvent %s: %s", event.getSubject(), ex.getMessage());
      return null;
    }
  }
}
