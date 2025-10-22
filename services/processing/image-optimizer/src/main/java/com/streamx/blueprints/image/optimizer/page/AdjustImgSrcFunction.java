package com.streamx.blueprints.image.optimizer.page;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
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
   * Receives the page from incoming channel and if the {@code eventType} is Page.TYPE_PUBLISHED -
   * adjusts all values of img src tags to use optimized version of the images, if such optimized
   * images are available. The adjusted page is published to the outgoing channel. When there are no
   * adjustments to be made, or when the {@code eventType} is Page.TYPE_UNPUBLISHED - the message is
   * relayed to the outgoing channel with no changes
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

    String pagePath = CloudEventUtils.getSubject(event);
    Page page = CloudEventUtils.getData(event, Page.class);
    if (Resource.isEmpty(page)) {
      log.warnf("Skipping adjusting incoming page [%s] - no content", pagePath);
      return event;
    }

    log.tracef("Processing page [%s] with eventTime %s", pagePath, event.getTime());

    if (!lowercasedAdjustedPagePathsPattern.matcher(pagePath.toLowerCase()).matches()) {
      log.tracef("Skipping adjusting incoming page [%s] - not matching path", pagePath);
      return event;
    }

    try {
      return createAdjustedPageEvent(page, pagePath, event);
    } catch (Exception e) {
      log.errorf(e, "Error adjusting content of page %s", pagePath);
      return event;
    }
  }

  private CloudEvent createAdjustedPageEvent(Page page, String pagePath, CloudEvent pageEvent) {
    String pageContent = page.getContentAsString();
    Optional<String> adjustedContent = imgSrcAdjuster.adjustPageContent(pageContent);
    if (adjustedContent.isEmpty()) {
      log.tracef("Relaying page %s with no adjustments", pagePath);
      return pageEvent;
    }

    Page adjustedPage = new Page(adjustedContent.get());
    log.tracef("Publishing adjusted page %s", pagePath);
    return CloudEventUtils.eventCopyWithData(pageEvent, adjustedPage).build();
  }
}
