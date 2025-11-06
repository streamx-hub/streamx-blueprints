package com.streamx.blueprints.image.generator;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.OptimizedAsset;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.image.generator.configuration.Configuration;
import io.cloudevents.CloudEvent;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OptimizeImageFunction {

  private Pattern lowercasedProcessedImagePathPattern;
  private String optimizedImageFileNameSuffixAndExtension;

  @Inject
  ImageOptimizer imageOptimizer;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @PostConstruct
  void init() {
    lowercasedProcessedImagePathPattern =
        Pattern.compile(configuration.processedImagePathPattern().toLowerCase());
    optimizedImageFileNameSuffixAndExtension =
        configuration.optimizedImageFileNameSuffix() + ImageOptimizer.OPTIMIZED_IMAGE_EXTENSION;
  }

  /**
   * Receives the asset from incoming channel and if the event is a publish event - creates its webp
   * representation and publishes it to outgoing channel. In case of any validation errors - the
   * webp image is not generated. If the event is an unpublish event - sends an unpublish event for
   * the optimized image to the outgoing channel.
   *
   * @return The optimized webp image ingestion event, or null
   */
  @Incoming(Channels.INCOMING_ASSETS)
  @Outgoing(Channels.OPTIMIZED_ASSETS)
  public CloudEvent process(CloudEvent event) {
    String filePath = CloudEventUtils.getSubject(event);

    if (!isValidFileName(filePath)) {
      log.tracef("Skipping optimizing incoming file [%s] - invalid file name", filePath);
      return null;
    }

    if (!lowercasedProcessedImagePathPattern.matcher(filePath.toLowerCase()).matches()) {
      log.tracef("Skipping optimizing incoming file [%s] - not matching path", filePath);
      return null;
    }

    log.tracef("Processing %s", filePath);
    return createOptimizedImageEvent(event, filePath);
  }

  static boolean isValidFileName(String filePath) {
    String nameWithExtension = FilenameUtils.getName(filePath);
    return nameWithExtension.matches(".+\\..+"); // non empty name + dot + non empty extension
  }

  private CloudEvent createOptimizedImageEvent(CloudEvent event, String filePath) {
    String optimizedImagePath = computePathForOptimizedImage(filePath);
    String eventType = event.getType();

    if (Asset.TYPE_PUBLISHED.equals(eventType)) {
      Asset asset = CloudEventUtils.getData(event, Asset.class);
      if (Resource.isEmpty(asset)) {
        log.warnf("Skipping optimizing [%s] - no content", filePath);
        return null;
      }
      OptimizedAsset optimizedImage = createOptimizedImage(asset, filePath);
      if (optimizedImage != null) {
        log.tracef("Publishing optimized image %s", optimizedImagePath);
        return CloudEventUtils.eventCopyWithData(event, optimizedImage)
            .withSubject(optimizedImagePath)
            .withType(OptimizedAsset.TYPE_PUBLISHED)
            .build();
      }
    } else if (Asset.TYPE_UNPUBLISHED.equals(eventType)) {
      log.tracef("Unpublishing optimized image %s", optimizedImagePath);
      return CloudEventUtils.eventCopyWithoutData(event)
          .withSubject(optimizedImagePath)
          .withType(OptimizedAsset.TYPE_UNPUBLISHED)
          .build();
    } else {
      log.tracef("Skipping optimizing incoming file [%s] - unsupported event type %s", filePath,
          eventType);
    }

    return null;
  }

  @Nullable
  private OptimizedAsset createOptimizedImage(Asset originalImage, String filePath) {
    try {
      byte[] optimizedImageBytes = imageOptimizer.asWebpImage(originalImage.getContentAsBytes());
      return new OptimizedAsset(optimizedImageBytes, originalImage.getType(), filePath);
    } catch (Throwable t) {
      log.errorf(t, "Error processing file %s", filePath);
      return null;
    }
  }

  String computePathForOptimizedImage(String filePath) {
    String fileNameAndExtension = FilenameUtils.getName(filePath);
    String fileName = FilenameUtils.getBaseName(filePath);
    String basePath = StringUtils.removeEnd(filePath, fileNameAndExtension);
    return basePath + fileName + optimizedImageFileNameSuffixAndExtension;
  }
}
