package com.streamx.blueprints.composition;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Composition;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.content.parser.datainsert.DataInsertHandler;
import com.streamx.content.parser.datainsert.Segment;
import com.streamx.content.parser.datainsert.SegmentDefineHandler;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CompositionFunction {

  @Inject
  Logger log;

  @Inject
  State state;

  @Incoming(Channels.INCOMING_LAYOUTS)
  @Outgoing(Channels.OUTGOING_PAGE_COMPOSE_REQUESTS)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  public Multi<CloudEvent> consumeLayout(CloudEvent layout) {
    String eventType = layout.getType();
    String subject = layout.getSubject();
    log.tracef("Consuming layout with subject %s and type %s", subject, eventType);
    return Multi.createFrom().items(createPageComposeRequests(layout));
  }

  @Incoming(Channels.INCOMING_COMPOSITIONS)
  @Outgoing(Channels.OUTGOING_PAGE_COMPOSE_REQUESTS)
  public CloudEvent consumeComposition(CloudEvent compositionEvent) {
    String compositionKey = compositionEvent.getSubject();
    String eventType = compositionEvent.getType();
    OffsetDateTime eventTime = compositionEvent.getTime();
    log.tracef("Consuming composition with key %s and type %s", compositionKey, eventType);

    if (Composition.TYPE_PUBLISHED.equals(eventType)) {
      Composition composition = CloudEventUtils.getData(compositionEvent, Composition.class);
      if (Resource.isEmpty(composition)) {
        log.warnf("Skipping processing empty incoming composition %s", compositionKey);
        return null;
      }
      return createPageComposeRequest(compositionKey, composition.getLayoutKey(),
          PageComposeRequest.TYPE_PUBLISHED, eventTime);
    }

    if (Composition.TYPE_UNPUBLISHED.equals(eventType)) {
      return createPageComposeRequest(compositionKey, null,
          PageComposeRequest.TYPE_UNPUBLISHED, eventTime);
    }

    log.warnf("Skipping processing event %s of unexpected type: %s", compositionKey, eventType);
    return null;
  }

  @Incoming(Channels.INCOMING_PAGE_COMPOSE_REQUESTS)
  @Outgoing(Channels.OUTGOING_PAGES)
  public CloudEvent generateComposedPageEvent(CloudEvent event) {
    PageComposeRequest request = CloudEventUtils.getData(event, PageComposeRequest.class);
    String compositionKey = request.compositionKey();
    String layoutKey = request.layoutKey();
    log.tracef("Consuming page compose request with composition key %s and layout key %s",
        compositionKey, layoutKey);

    if (PageComposeRequest.TYPE_PUBLISHED.equals(event.getType())) {
      Composition composition = state.getComposition(compositionKey);
      Layout layout = state.getLayout(layoutKey);

      if (ableToGeneratePage(layout, layoutKey, composition, compositionKey)) {
        Page page = composePage(composition, layout);
        return CloudEventUtils.eventWithData(compositionKey, Page.TYPE_PUBLISHED, page,
            event.getTime());
      }
    } else if (PageComposeRequest.TYPE_UNPUBLISHED.equals(event.getType())) {
      return CloudEventUtils.eventWithoutData(compositionKey, Page.TYPE_UNPUBLISHED,
          event.getTime());
    }
    return null;
  }

  private Stream<CloudEvent> createPageComposeRequests(CloudEvent layout) {
    String layoutKey = layout.getSubject();

    String type = Layout.TYPE_PUBLISHED.equals(layout.getType())
        ? PageComposeRequest.TYPE_PUBLISHED
        : PageComposeRequest.TYPE_UNPUBLISHED;

    return state
        .getCompositionKeysByLayoutKey(layoutKey)
        .map(compositionKey ->
            createPageComposeRequest(compositionKey, layoutKey, type, layout.getTime()));
  }

  private CloudEvent createPageComposeRequest(String compositionKey, String layoutKey,
      String eventType, OffsetDateTime eventTime) {
    log.tracef("Creating page compose request for composition %s and layout %s",
        compositionKey, layoutKey);
    PageComposeRequest data = new PageComposeRequest(compositionKey, layoutKey);
    return CloudEventUtils.eventWithData(compositionKey, eventType, data, eventTime);
  }

  private boolean ableToGeneratePage(Layout layout, String layoutKey,
      Composition composition, String compositionKey) {
    if (layout == null) {
      log.tracef("Skipping generating page, layout %s is not available", layoutKey);
      return false;
    }

    if (composition == null) {
      log.tracef("Skipping generating page, composition %s is not published", compositionKey);
      return false;
    }

    return true;
  }

  Page composePage(Composition composition, Layout layout) {
    List<Segment> segments = SegmentDefineHandler.parseSegments(composition.getContentAsString());
    String pageContent = DataInsertHandler.insert(layout.getContentAsString(), segments);
    return new Page(pageContent, layout.getType());
  }
}
