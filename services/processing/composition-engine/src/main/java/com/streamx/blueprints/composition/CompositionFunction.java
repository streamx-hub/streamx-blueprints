package com.streamx.blueprints.composition;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Composition;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Page;
import com.streamx.content.parser.datainsert.DataInsertHandler;
import com.streamx.content.parser.datainsert.Segment;
import com.streamx.content.parser.datainsert.SegmentDefineHandler;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CompositionFunction {

  @Inject
  Logger log;

  Map<String, Layout> layoutsStore = new HashMap<>();

  Map<String, Composition> compositionsStore = new HashMap<>();

  @Incoming(Channels.INCOMING_LAYOUTS)
  @Outgoing(Channels.OUTGOING_PAGE_COMPOSE_REQUESTS)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  public Multi<CloudEvent> consumeLayout(CloudEvent layout) {
    if (CloudEventUtils.isPublishingType(layout.getType())) {
      layoutsStore.put(layout.getSubject(), CloudEventUtils.getData(layout, Layout.class));
    } else if (CloudEventUtils.isUnpublishingType(layout.getType())) {
      layoutsStore.remove(layout.getSubject());
    }
    try {
      return Multi.createFrom().items(createPageComposeRequests(layout));
    } catch (Exception e) {
      return Multi.createFrom().empty();
    }
  }

  @Incoming(Channels.INCOMING_COMPOSITIONS)
  @Outgoing(Channels.OUTGOING_PAGE_COMPOSE_REQUESTS)
  public CloudEvent consumeComposition(CloudEvent compositionEvent) {
    String compositionKey = compositionEvent.getSubject();
    if (CloudEventUtils.isPublishingType(compositionEvent.getType())) {
      Composition composition = CloudEventUtils.getData(compositionEvent, Composition.class);
      compositionsStore.put(compositionKey, composition);
      return CloudEventUtils.eventWithData(
          new PageComposeRequest(compositionKey, composition.getLayoutKey()),
          PageComposeRequest.TYPE_PUBLISHED,
          compositionKey,
          compositionEvent.getTime());
    } else if (CloudEventUtils.isUnpublishingType(compositionEvent.getType())) {
      compositionsStore.remove(compositionKey);
      return CloudEventUtils.eventWithData(
          new PageComposeRequest(compositionKey, null),
          PageComposeRequest.TYPE_UNPUBLISHED,
          compositionKey,
          compositionEvent.getTime());
    }
    return null;
  }

  @Incoming(Channels.INCOMING_PAGE_COMPOSE_REQUESTS)
  @Outgoing(Channels.OUTGOING_PAGES)
  public CloudEvent generateComposedPageEvent(CloudEvent event) {
    PageComposeRequest request = CloudEventUtils.getData(event, PageComposeRequest.class);
    String compositionKey = request.compositionKey();
    String layoutKey = request.layoutKey();

    if (PageComposeRequest.TYPE_PUBLISHED.equals(event.getType())) {
      Composition composition = compositionsStore.get(compositionKey);
      Layout layout = layoutsStore.get(layoutKey);

      if (ableToGeneratePage(layout, layoutKey, composition, compositionKey)) {
        Page page = composePage(composition, layout);
        return CloudEventUtils.eventWithData(page, Page.TYPE_PUBLISHED,
            compositionKey, event.getTime());
      }
    } else if (PageComposeRequest.TYPE_UNPUBLISHED.equals(event.getType())) {
      return CloudEventUtils.eventWithoutData(Page.TYPE_UNPUBLISHED,
          compositionKey, event.getTime());
    }
    return null;
  }

  private Stream<CloudEvent> createPageComposeRequests(CloudEvent layout) {
    String layoutKey = layout.getSubject();

    String type = Layout.TYPE_PUBLISHED.equals(layout.getType())
        ? PageComposeRequest.TYPE_PUBLISHED
        : PageComposeRequest.TYPE_UNPUBLISHED;

    return compositionsStore.entrySet().stream()
        .filter(entry ->
            entry.getValue() != null && entry.getValue().getLayoutKey().equals(layoutKey))
        .map(Entry::getKey)
        .map(compositionKey ->
            CloudEventUtils.eventWithData(
                new PageComposeRequest(compositionKey, layoutKey),
                type,
                compositionKey,
                layout.getTime()));
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
