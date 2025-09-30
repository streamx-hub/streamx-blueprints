package com.streamx.blueprints.externalresources.services;

import com.streamx.blueprints.externalresources.configuration.Configuration;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

@ApplicationScoped
public class UrlComputationService {

  private static final String NON_STANDARD_CHARS_REGEX = "[^a-zA-Z0-9/.-]";
  private static final String NON_STANDARD_CHARS_REPLACEMENT = "_";

  private URI baseUriForRelativePaths;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @PostConstruct
  void init() {
    baseUriForRelativePaths = URI.create(configuration.baseUrlForRelativePaths());
  }

  public String computeAbsoluteUrlRelativeToConfiguredBaseUrl(String relativeUrl) {
    return computeAbsoluteUrl(baseUriForRelativePaths, relativeUrl);
  }

  public String computeAbsoluteUrl(String parentAbsoluteUrl, String relativeOrAbsoluteUrl) {
    if (isAbsoluteHttpUrl(relativeOrAbsoluteUrl)) {
      return validatedAbsoluteUrl(relativeOrAbsoluteUrl);
    }
    return computeAbsoluteUrl(URI.create(parentAbsoluteUrl), relativeOrAbsoluteUrl);
  }

  private String computeAbsoluteUrl(URI parentAbsoluteUri, String relativeUrl) {
    try {
      return parentAbsoluteUri.resolve(relativeUrl).toString();
    } catch (IllegalArgumentException ex) {
      String encodedPath = encodedRelativeUrl(relativeUrl);
      log.warnf("Error computing absolute url for %s: %s. Encoding it to %s",
          relativeUrl, ex.getMessage(), encodedPath);
      return parentAbsoluteUri.resolve(encodedPath).toString();
    }
  }

  private static String validatedAbsoluteUrl(String absoluteUrl) {
    try {
      return URI.create(absoluteUrl).toString();
    } catch (IllegalArgumentException ex) {
      int pathStartIndex = StringUtils.ordinalIndexOf(absoluteUrl, "/", 3);
      if (pathStartIndex != -1) {
        String partBeforePath = absoluteUrl.substring(0, pathStartIndex);
        String path = absoluteUrl.substring(pathStartIndex);
        return partBeforePath + encodedRelativeUrl(path);
      } else {
        throw ex;
      }
    }
  }

  private static String encodedRelativeUrl(String relativeUrl) {
    int queryStartIndex = relativeUrl.indexOf("?");
    if (queryStartIndex == -1) {
      return encodedPath(relativeUrl);
    }
    String path = relativeUrl.substring(0, queryStartIndex);
    String queryString = relativeUrl.substring(queryStartIndex);
    return encodedPath(path) + queryString;
  }

  private static String encodedPath(String path) {
    try {
      return new URI(null, null, path, null).toASCIIString();
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  public String asStreamxKeyRelativeToConfiguredBaseUrl(String absoluteUrl) {
    URI uri = URI.create(absoluteUrl);
    if (uri.equals(baseUriForRelativePaths)) {
      return asStreamxKey("/");
    }
    String relativeUrl = baseUriForRelativePaths.relativize(uri).toString();
    return asStreamxKey(relativeUrl);
  }

  public boolean isAbsoluteHttpUrl(String relativeOrAbsoluteUrl) {
    return relativeOrAbsoluteUrl.matches("^https?://.*");
  }

  public String asStreamxKey(String absoluteOrRelativeUrl) {
    String extension = getExtensionFromUrl(absoluteOrRelativeUrl);
    boolean hasExtension = extension != null;

    String urlWithExtensionAtEnd = (absoluteOrRelativeUrl.contains("?") && hasExtension)
        ? absoluteOrRelativeUrl + extension
        : absoluteOrRelativeUrl;

    String sanitizedKey = urlWithExtensionAtEnd
        .replaceFirst("://", "_")
        .replaceAll(NON_STANDARD_CHARS_REGEX, NON_STANDARD_CHARS_REPLACEMENT);

    return StringUtils.prependIfMissing(sanitizedKey, "/");
  }

  /**
   * @return extension of the uri path (with the leading dot included), or null if the
   * extension cannot be detected.
   */
  @Nullable
  private String getExtensionFromUrl(String absoluteUrl) {
    try {
      URI uri = URI.create(absoluteUrl);
      String extension = FilenameUtils.getExtension(uri.getPath());
      return StringUtils.isEmpty(extension) ? null : "." + extension;
    } catch (IllegalArgumentException ex) {
      log.warnf("Cannot get extension from illegal URL: %s", absoluteUrl);
      return null;
    }
  }

}
