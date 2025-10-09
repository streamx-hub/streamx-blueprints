package com.streamx.blueprints.image.optimizer.image;

import static com.streamx.blueprints.image.optimizer.image.ImageOptimizer.OPTIMIZED_IMAGE_EXTENSION;
import static java.util.Objects.requireNonNull;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.image.optimizer.Channels;
import com.streamx.blueprints.image.optimizer.configuration.Configuration;
import com.streamx.blueprints.image.optimizer.image.exceptions.ImageOptimizationException;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OptimizeImageFunction {

  private Pattern lowercasedOptimizedFilePathsPattern;
  private String optimizedImageFileNameSuffixAndExtension;
  private ImageOptimizer imageOptimizer;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  AssetEventTypeStore assetActionStore;

  @Inject
  OptimizedImagePathsService optimizedImagePathsService;

  @PostConstruct
  void init() {
    lowercasedOptimizedFilePathsPattern =
        Pattern.compile(configuration.optimizedFilePathsPattern().toLowerCase());
    optimizedImageFileNameSuffixAndExtension =
        configuration.optimizedImageFileNameSuffix() + OPTIMIZED_IMAGE_EXTENSION;
    imageOptimizer = new ImageOptimizer(configuration.webpConversion());
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
    log.tracef("Processing %s", filePath);

    assetActionStore.registerAsset(filePath, event.getType());

    if (filePath.endsWith(optimizedImageFileNameSuffixAndExtension)) {
      log.tracef("Skipping already optimized image [%s]", filePath);
      return null;
    }

    if (!lowercasedOptimizedFilePathsPattern.matcher(filePath.toLowerCase()).matches()) {
      log.tracef("Skipping optimizing incoming file [%s] - not matching path", filePath);
      return null;
    }

    return createOptimizedImageEvent(event, filePath);
  }

  private CloudEvent createOptimizedImageEvent(CloudEvent event, String filePath) {
    String optimizedImagePath = optimizedImagePathsService.computePathForOptimizedImage(filePath);
    String eventType = event.getType();
    OffsetDateTime eventTime = event.getTime();

    if (Asset.TYPE_PUBLISHED.equals(eventType)) {
      Asset asset = requireNonNull(CloudEventUtils.getData(event, Asset.class));
      Asset optimizedImage = createOptimizedImage(asset, filePath);
      return CloudEventUtils.eventWithData(optimizedImage, Asset.TYPE_PUBLISHED, optimizedImagePath,
          eventTime);
    }
    if (Asset.TYPE_UNPUBLISHED.equals(eventType)) {
      return CloudEventUtils.eventWithoutData(Asset.TYPE_UNPUBLISHED, optimizedImagePath,
          eventTime);
    }

    log.tracef("Skipping optimizing incoming file [%s] - unsupported event type %s", filePath,
        eventType);
    return null;
  }

  private Asset createOptimizedImage(Asset originalImage, String filePath) {
    try {
      byte[] optimizedImageBytes = imageOptimizer.asWebpImage(
          originalImage.getContent().array()
      );
      return new Asset(optimizedImageBytes);
    } catch (Exception e) {
      throw new ImageOptimizationException("Error processing file " + filePath, e);
    }
  }
}
