package com.streamx.blueprints.image.optimization.page;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.image.optimization.configuration.Configuration;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.regex.Pattern;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AdjustImgSrcFunction {

  static final String INCOMING_CHANNEL = "incoming-pages";
  static final String OUTGOING_CHANNEL = "outgoing-pages";

  private Pattern lowercasedAdjustedPagePathsPattern;

  @Inject
  ImgSrcAdjuster imgSrcAdjuster;

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @PostConstruct
  void init() {
    lowercasedAdjustedPagePathsPattern =
        Pattern.compile(configuration.adjustedPagePathsPattern().toLowerCase());
  }

  /**
   * Receives the page from incoming channel and if the {@code action} is PUBLISH - adjusts all
   * values of img src tags to use optimized version of the images, if such optimized images are
   * available. The adjusted page is published to the outgoing channel. When there are no
   * adjustments to be made, or when the {@code action} is UNPUBLISH - the message is relayed to
   * outgoing channel with no changes
   *
   * @return The adjusted page message or null if nothing to adjust
   */
  @Incoming(INCOMING_CHANNEL)
  @Outgoing(OUTGOING_CHANNEL)
  public GenericPayload<Page> process(Page page, Key key, Action action, EventTime eventTime) {
    String pagePath = key.getValue();
    log.tracef("Processing page [%s] with eventTime %s", pagePath, eventTime);

    if (!PUBLISH.equals(action)) {
      return GenericPayload.of(page);
    }

    if (!lowercasedAdjustedPagePathsPattern.matcher(pagePath.toLowerCase()).matches()) {
      log.tracef("Skipping adjusting incoming page [%s] - not matching path", pagePath);
      return GenericPayload.of(page);
    }

    try {
      return createAdjustedPagePayload(page);
    } catch (Exception e) {
      log.errorf(e, "Error adjusting content of page " + pagePath);
      return GenericPayload.of(page);
    }
  }

  private GenericPayload<Page> createAdjustedPagePayload(Page page) {
    String pageContent = page.getContentAsString();
    Optional<String> adjustedContent = imgSrcAdjuster.adjustPageContent(pageContent);
    if (adjustedContent.isEmpty()) {
      return GenericPayload.of(page);
    }

    Page adjustedPage = new Page(adjustedContent.get());
    return GenericPayload.of(adjustedPage);
  }
}
