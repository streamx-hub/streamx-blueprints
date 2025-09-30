package com.streamx.blueprints.image.optimization.image;


import static com.streamx.blueprints.image.optimization.image.ImageOptimizer.OPTIMIZED_IMAGE_EXTENSION;

import com.streamx.blueprints.image.optimization.configuration.Configuration;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OptimizedImagePathsService {

  private static final Pattern FILE_NAME_AND_EXTENSION_AND_QUERY_STRING = Pattern.compile(
      "([^.]+)" // file name (up to dot, not including it)
      + "\\." // dot between file name and extension
      + "([^?]+)" // extension (up to ? char, not including it)
      + "(\\?.+)?" // optional query string (beginning with ?)
  );
  private String optimizedImageFileNameSuffixAndExtension;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @PostConstruct
  void init() {
    optimizedImageFileNameSuffixAndExtension =
        configuration.optimizedImageFileNameSuffix() + OPTIMIZED_IMAGE_EXTENSION;
  }

  public String computePathForOptimizedImage(String filePath) {
    String fileNameAndExtension = getFileNameAndExtension(filePath);
    Matcher matcher = FILE_NAME_AND_EXTENSION_AND_QUERY_STRING.matcher(fileNameAndExtension);
    if (matcher.find()) {
      String fileName = matcher.group(1);
      String queryString = Optional.ofNullable(matcher.group(3)).orElse("");
      String basePath = StringUtils.removeEnd(filePath, fileNameAndExtension);
      return basePath + fileName + optimizedImageFileNameSuffixAndExtension + queryString;
    } else {
      log.errorf("Error creating path for optimized image, for input file %s", filePath);
      return filePath;
    }
  }

  private static String getFileNameAndExtension(String filePath) {
    String normalizedFilePath = filePath.replace('\\', '/');
    if (normalizedFilePath.contains("/")) {
      return StringUtils.substringAfterLast(normalizedFilePath, '/');
    }
    return filePath;
  }

  public boolean isOptimizedImagePath(String filePath) {
    return StringUtils.endsWith(filePath, optimizedImageFileNameSuffixAndExtension);
  }
}
