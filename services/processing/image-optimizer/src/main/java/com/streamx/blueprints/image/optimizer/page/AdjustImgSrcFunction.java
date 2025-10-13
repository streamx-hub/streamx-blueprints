package com.streamx.blueprints.image.optimizer.page;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.image.optimizer.Channels;
import com.streamx.blueprints.image.optimizer.configuration.Configuration;
import io.cloudevents.CloudEvent;
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
   * @return The adjusted page message or relayed event if nothing to adjust
   */
  @Incoming(Channels.INCOMING_PAGES)
  @Outgoing(Channels.OUTGOING_PAGES)
  public CloudEvent process(CloudEvent event) {
    String eventType = event.getType();
    if (!Page.TYPE_PUBLISHED.equals(eventType)) {
      return event;
    }

    Page page = CloudEventUtils.getDataOrThrow(event, Page.class);
    String pagePath = CloudEventUtils.getSubject(event);
    log.tracef("Processing page [%s] with eventTime %s", pagePath, event.getTime());

    if (!lowercasedAdjustedPagePathsPattern.matcher(pagePath.toLowerCase()).matches()) {
      log.tracef("Skipping adjusting incoming page [%s] - not matching path", pagePath);
      return event;
    }

    try {
      return createAdjustedPageEvent(page, event);
    } catch (Exception e) {
      log.errorf(e, "Error adjusting content of page " + pagePath);
      return event;
    }
  }

  private CloudEvent createAdjustedPageEvent(Page page, CloudEvent pageEvent) {
    String pageContent = page.getContentAsString();
    Optional<String> adjustedContent = imgSrcAdjuster.adjustPageContent(pageContent);
    if (adjustedContent.isEmpty()) {
      return pageEvent;
    }

    Page adjustedPage = new Page(adjustedContent.get());
    return CloudEventUtils.eventWithData(
        adjustedPage,
        pageEvent.getType(),
        pageEvent.getSubject(),
        pageEvent.getTime());
  }
}
