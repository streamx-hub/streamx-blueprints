package com.streamx.blueprints.image.optimizer.page;

import com.streamx.blueprints.image.optimizer.image.AssetActionStore;
import com.streamx.blueprints.image.optimizer.image.OptimizedImagePathsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;
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
  OptimizedImagePathsService optimizedImagePathsService;

  @Inject
  AssetActionStore assetActionStore;

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
    return document.select("img[src]")
        .stream()
        .map(this::adjustImgSrc)
        .collect(Collectors.toSet())
        .contains(true);
  }

  /**
   * @return true if image was adjusted, false if no changes
   */
  private boolean adjustImgSrc(Element img) {
    String imagePath = img.attr("src");
    if (optimizedImagePathsService.isOptimizedImagePath(imagePath)) {
      // the image already uses optimized image path
      return false;
    }

    String optimizedImagePath = optimizedImagePathsService.computePathForOptimizedImage(imagePath);
    String optimizedImagePathWithoutQueryString =
        StringUtils.substringBeforeLast(optimizedImagePath, "?");
    if (assetActionStore.isOptimizedImagePublished(optimizedImagePathWithoutQueryString)) {
      log.tracef("Updating %s to %s", imagePath, optimizedImagePath);
      img.attr("src", optimizedImagePath);
      return true;
    }

    return false;
  }
}
