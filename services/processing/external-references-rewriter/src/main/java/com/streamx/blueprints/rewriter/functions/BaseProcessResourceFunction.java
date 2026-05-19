package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.data.ResourceData;
import com.streamx.blueprints.rewriter.functions.settings.BaseProcessingSettings;
import com.streamx.blueprints.rewriter.services.UrlComputationService;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;
import org.jboss.logging.Logger;

public abstract class BaseProcessResourceFunction {

  @Inject
  Logger log;

  @Inject
  UrlComputationService urlComputationService;

  @Inject
  Configuration configuration;

  @Inject
  Instance<BaseProcessingSettings<?>> processingSettings;

  protected Optional<ProcessingContext> resolveContext(CloudEvent event) {
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

  protected ResourceData toResourceData(ProcessingContext ctx) {

    return collectResourceData(
        ctx.resourcePath(),
        ctx.payload().getContentAsString(),
        ctx.settings().getHandledResourceClass(),
        ctx.payloadType()
    );
  }

  public CloudEvent createDownloadRequest(ExternalResource resource) {

    String absoluteUrl = resource.getAbsoluteUrl();
    String streamxKey = resource.getStreamxKey();

    DownloadRequest downloadRequest = new DownloadRequest(
        absoluteUrl,
        streamxKey,
        configuration.emittedPageType(),
        configuration.emittedWebResourceType(),
        configuration.emittedAssetType()
    );

    log.tracef("Sending download request for %s", absoluteUrl);

    return CloudEventUtils.eventWithData(
        streamxKey,
        DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE,
        downloadRequest
    );
  }

  private ResourceData collectResourceData(String path, String content,
      Class<? extends Resource> handledResourceType, String payloadType) {
    if (WebResource.class.isAssignableFrom(handledResourceType)) {
      // web resources later become available by http as files, so must sanitize their paths
      String sanitizedResourcePath = urlComputationService.asStreamxKey(path);
      String resourceAbsoluteUrl = urlComputationService
          .computeAbsoluteUrlRelativeToConfiguredBaseUrl(sanitizedResourcePath);
      String resourceStreamxKey = urlComputationService
          .asStreamxKeyRelativeToConfiguredBaseUrl(resourceAbsoluteUrl);
      return new ResourceData(
          resourceAbsoluteUrl, resourceStreamxKey, content, payloadType);
    } else {
      return new ResourceData(
          configuration.baseUrlForRelativePaths(), path, content, payloadType);
    }
  }
  
  public record ProcessingContext(Resource payload, String payloadType,
                                  String resourcePath, BaseProcessingSettings<?> settings) {
  }
}