package com.streamx.blueprints.rewriter;

import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@ApplicationScoped
class ImgSrcAdjuster {

  @Inject
  Logger log;

  @Inject
  OptimizedAssetsStore optimizedAssetsStore;

  /**
   * @return adjusted page content, or empty optional if no adjustments were applied
   */
  public Optional<String> adjustPageContent(String pageContent) {
    Document document = Jsoup.parse(pageContent);
    boolean adjusted = adjustDocument(document);
    if (adjusted) {
      return Optional.of(document.outerHtml());
    } else {
      return Optional.empty();
    }
  }

  /**
   * @return true if document was adjusted, false if no changes. The document is considered adjusted
   * if any of the images it contains was adjusted.
   */
  private boolean adjustDocument(Document document) {
    boolean adjusted = false;
    for (Element image : document.select("img[src]")) {
      adjusted |= adjustImageReference(image, "src");
    }
    for (Element metadata : document.select("meta[property=og:image][content]")) {
      adjusted |= adjustImageReference(metadata, "content");
    }
    return adjusted;
  }

  /**
   * @return true if image was adjusted, false if no changes
   */
  private boolean adjustImageReference(Element element, String attribute) {
    String imagePath = element.attr(attribute);
    String imagePathWithoutQueryString = StringUtils.substringBeforeLast(imagePath, "?");
    String queryString = StringUtils.substringAfterLast(imagePath, "?");
    String optimizedImagePath = getOptimizedImagePath(imagePathWithoutQueryString, queryString);

    if (optimizedImagePath != null) {
      log.tracef("Updating %s to %s", imagePath, optimizedImagePath);
      element.attr(attribute, optimizedImagePath);
      return true;
    }

    return false;
  }

  @Nullable
  private String getOptimizedImagePath(String imagePathWithoutQueryString, String queryString) {
    return Optional
        .ofNullable(optimizedAssetsStore.getOptimizedAssetPath(imagePathWithoutQueryString))
        .map(path -> queryString.isBlank() ? path : path + "?" + queryString)
        .orElse(null);
  }
}
