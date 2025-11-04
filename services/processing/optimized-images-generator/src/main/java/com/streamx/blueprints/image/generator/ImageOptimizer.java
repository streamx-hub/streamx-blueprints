package com.streamx.blueprints.image.generator;

import com.sksamuel.scrimage.AwtImage;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.metadata.ImageMetadata;
import com.sksamuel.scrimage.nio.ImmutableImageLoader;
import com.sksamuel.scrimage.webp.WebpWriter;
import com.streamx.blueprints.image.generator.configuration.Configuration;
import com.streamx.blueprints.image.generator.exceptions.NotAnImageException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@ApplicationScoped
class ImageOptimizer {

  static final String OPTIMIZED_IMAGE_EXTENSION = ".webp";

  private WebpWriter webpWriter;

  @Inject
  Configuration configuration;

  @PostConstruct
  void init() {
    webpWriter = WebpWriterFactory.createWriterInstance(configuration.webpConversion());
  }

  synchronized byte[] asWebpImage(byte[] imageBytes) throws IOException {
    if (imageBytes == null) {
      throw new NotAnImageException("Null image bytes array");
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    AwtImage awtImage = toAwtImage(imageBytes);
    ImageMetadata imageMetadata = ImageMetadata.fromBytes(imageBytes);
    webpWriter.write(awtImage, imageMetadata, outputStream);
    return outputStream.toByteArray();
  }

  private static AwtImage toAwtImage(byte[] imageContent) throws IOException {
    ImmutableImageLoader imageLoader = ImmutableImage.loader().withJavaxImageReaders();
    try {
      BufferedImage bufferedImage = imageLoader.fromBytes(imageContent).awt();
      return new AwtImage(bufferedImage);
    } catch (IOException ex) {
      throw new NotAnImageException("Error reading the data as an image", ex);
    }
  }

}
