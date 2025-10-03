package com.streamx.blueprints.image.optimizer.image;

import com.sksamuel.scrimage.AwtImage;
import com.sksamuel.scrimage.metadata.ImageMetadata;
import com.sksamuel.scrimage.webp.WebpWriter;
import com.streamx.blueprints.image.optimizer.configuration.Configuration;
import com.streamx.blueprints.image.optimizer.image.exceptions.NotAnImageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.imageio.ImageIO;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ImageOptimizer {

  static final String OPTIMIZED_IMAGE_EXTENSION = ".webp";

  private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);
  private static final String WARM_UP_IMAGE_RESOURCE_PATH = "warm-up-image.png";

  private final WebpWriter webpWriter;

  static {
    processWarmUpImage();
  }

  // To enable multi-thread access, the scrimage library must be fully initialized first.
  // Perform initialization by processing a warm-up small image
  private static void processWarmUpImage() {
    try {
      URL warmUpImageUrl = ImageOptimizer.class.getClassLoader()
          .getResource(WARM_UP_IMAGE_RESOURCE_PATH);
      ImageOptimizer imageOptimizer = new ImageOptimizer(WebpWriter.DEFAULT);
      byte[] ignored = imageOptimizer.asWebpImage(IOUtils.toByteArray(warmUpImageUrl));
    } catch (Exception ex) {
      log.error("Error processing warm-up image", ex);
    }
  }

  ImageOptimizer(Configuration configuration) {
    this(WebpWriterFactory.createWriterInstance(configuration));
  }

  ImageOptimizer(WebpWriter webpWriter) {
    this.webpWriter = webpWriter;
  }

  byte[] asWebpImage(byte[] imageBytes) throws IOException {
    if (imageBytes == null) {
      throw new NotAnImageException("Null image bytes array");
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (InputStream imageContentStream = new ByteArrayInputStream(imageBytes)) {
      AwtImage awtImage = toAwtImage(imageContentStream);
      ImageMetadata imageMetadata = ImageMetadata.fromStream(imageContentStream);
      webpWriter.write(awtImage, imageMetadata, outputStream);
    }
    return outputStream.toByteArray();
  }

  private static AwtImage toAwtImage(InputStream imageContent) throws IOException {
    BufferedImage bufferedImage = ImageIO.read(imageContent);
    if (bufferedImage == null) {
      throw new NotAnImageException("Error reading the data as an image");
    }
    return new AwtImage(bufferedImage);
  }

}
