package com.streamx.blueprints.image.optimization.image;


import static com.streamx.blueprints.image.optimization.image.ImageOptimizer.OPTIMIZED_IMAGE_EXTENSION;

import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.image.optimization.configuration.Configuration;
import com.streamx.blueprints.image.optimization.image.exceptions.ImageOptimizationException;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.regex.Pattern;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OptimizeImageFunction {

  public static final String INCOMING_ASSETS_CHANNEL = "incoming-assets";
  public static final String OPTIMIZED_ASSETS_CHANNEL = "optimized-assets";

  private Pattern lowercasedOptimizedFilePathsPattern;
  private String optimizedImageFileNameSuffixAndExtension;
  private ImageOptimizer imageOptimizer;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  OptimizedImagePathsService optimizedImagePathsService;

  @PostConstruct
  void init() {
    lowercasedOptimizedFilePathsPattern =
        Pattern.compile(configuration.optimizedFilePathsPattern().toLowerCase());
    optimizedImageFileNameSuffixAndExtension =
        configuration.optimizedImageFileNameSuffix() + OPTIMIZED_IMAGE_EXTENSION;
    imageOptimizer = new ImageOptimizer(configuration);
  }

  /**
   * Receives the asset from incoming channel and if the {@code action} is PUBLISH - creates its
   * webp representation and publishes it to outgoing channel.
   * In case of any validation errors - the webp image is not generated.
   * If the {@code action} is UNPUBLISH - sends an unpublish message for the optimized image
   * to the outgoing channel.
   *
   * @return The optimized webp image ingestion message, or null
   */
  @Incoming(INCOMING_ASSETS_CHANNEL)
  @Outgoing(OPTIMIZED_ASSETS_CHANNEL)
  public GenericPayload<Asset> process(Asset asset, Key key, Action action, EventTime eventTime) {
    String filePath = key.getValue();
    log.tracef("Processing file [%s] with eventTime %s", filePath, eventTime);

    if (filePath.endsWith(optimizedImageFileNameSuffixAndExtension)) {
      log.tracef("Skipping already optimized image [%s]", filePath);
      return null;
    }

    if (!lowercasedOptimizedFilePathsPattern.matcher(filePath.toLowerCase()).matches()) {
      log.tracef("Skipping optimizing incoming file [%s] - not matching path", filePath);
      return null;
    }

    return createOptimizedImagePayload(asset, action, eventTime, filePath);
  }

  private GenericPayload<Asset> createOptimizedImagePayload(Asset asset, Action action,
      EventTime eventTime, String filePath) {
    String optimizedImagePath = optimizedImagePathsService.computePathForOptimizedImage(filePath);
    Metadata metadata = createMetadata(optimizedImagePath, eventTime, action);
    if (Action.PUBLISH.equals(action)) {
      Asset optimizedImage = createOptimizedImage(asset, filePath);
      return GenericPayload.of(optimizedImage, metadata);
    }
    if (Action.UNPUBLISH.equals(action)) {
      return GenericPayload.of(null, metadata);
    }

    log.tracef("Skipping optimizing incoming file [%s] - unsupported action %s", filePath, action);
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

  private static Metadata createMetadata(String filePath, EventTime eventTime, Action action) {
    return Metadata.of(Key.of(filePath), eventTime, action);
  }
}
