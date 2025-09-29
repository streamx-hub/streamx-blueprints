package dev.streamx.blueprints.composition;

import com.streamx.content.parser.datainsert.DataInsertHandler;
import com.streamx.content.parser.datainsert.Segment;
import com.streamx.content.parser.datainsert.SegmentDefineHandler;
import dev.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import dev.streamx.blueprints.data.Composition;
import dev.streamx.blueprints.data.Layout;
import dev.streamx.blueprints.data.Page;
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

  static final String INCOMING_LAYOUTS_CHANNEL = "layouts";
  static final String INCOMING_COMPOSITIONS_CHANNEL = "compositions";
  static final String OUTGOING_PAGES_CHANNEL = "pages";

  static final String INCOMING_PAGE_COMPOSE_REQUESTS_CHANNEL = "incoming-page-compose-requests";
  static final String OUTGOING_PAGE_COMPOSE_REQUESTS_CHANNEL = "outgoing-page-compose-requests";

  @Inject
  Logger log;

  Map<String, Layout> layoutsStore = new HashMap<>();

  Map<String, Composition> compositionsStore = new HashMap<>();

  @Incoming(INCOMING_LAYOUTS_CHANNEL)
  @Outgoing(OUTGOING_PAGE_COMPOSE_REQUESTS_CHANNEL)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  public Multi<CloudEvent> consumeLayout(CloudEvent layout) {
    if (CloudEventUtils.isPublishingType(layout.getType())) {
      layoutsStore.put(layout.getSubject(), CloudEventUtils.getData(layout, Layout.class));
    } if (CloudEventUtils.isUnpublishingType(layout.getType())) {
      layoutsStore.remove(layout.getSubject());
    }
    try {
      return Multi.createFrom()
          .items(createPageComposeRequests(layout));
    } catch (Exception e) {
      return Multi.createFrom().empty();
    }
  }

  @Incoming(INCOMING_COMPOSITIONS_CHANNEL)
  @Outgoing(OUTGOING_PAGE_COMPOSE_REQUESTS_CHANNEL)
  public CloudEvent consumeComposition(CloudEvent compositionEvent) {
    String compositionKey = compositionEvent.getSubject();
    if (CloudEventUtils.isPublishingType(compositionEvent.getType())) {
      Composition composition = CloudEventUtils.getData(compositionEvent, Composition.class);
      compositionsStore.put(compositionKey, composition);
      return CloudEventUtils.builderWithJsonData(
              new PageComposeRequest(compositionKey, composition.getLayoutKey()))
          .withTime(compositionEvent.getTime())
          .withSubject(compositionKey)
          .withType(PageComposeRequest.TYPE_PUBLISHED)
          .build();
    } else if (CloudEventUtils.isUnpublishingType(compositionEvent.getType())) {
      compositionsStore.remove(compositionKey);
      return CloudEventUtils.builderWithJsonData(new PageComposeRequest(compositionKey, null))
          .withTime(compositionEvent.getTime())
          .withType(PageComposeRequest.TYPE_UNPUBLISHED)
          .withSubject(compositionKey)
          .build();
    }
    return null;
  }

  @Incoming(INCOMING_PAGE_COMPOSE_REQUESTS_CHANNEL)
  @Outgoing(OUTGOING_PAGES_CHANNEL)
  public CloudEvent generateComposedPageMessage(CloudEvent pageCompositionRequest) {
    PageComposeRequest request = CloudEventUtils.getData(pageCompositionRequest, PageComposeRequest.class);
    String compositionKey = request.compositionKey();
    String layoutKey = request.layoutKey();

    if (PageComposeRequest.TYPE_PUBLISHED.equals(pageCompositionRequest.getType())) {
      Composition composition = compositionsStore.get(compositionKey);
      Layout layout = layoutsStore.get(layoutKey);

      if (ableToGeneratePage(layout, layoutKey, composition, compositionKey)) {
        Page page = composePage(composition, layout);
        return CloudEventUtils.builderWithJsonData(page)
            .withType(Page.TYPE_PUBLISHED)
            .withSubject(compositionKey)
            .withTime(pageCompositionRequest.getTime())
            .build();
      }
    } else if (PageComposeRequest.TYPE_UNPUBLISHED.equals(pageCompositionRequest.getType())) {
      return CloudEventUtils.builder()
          .withType(Page.TYPE_UNPUBLISHED)
          .withSubject(compositionKey)
          .withTime(pageCompositionRequest.getTime())
          .build();
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
            CloudEventUtils
                .builderWithJsonData(new PageComposeRequest(compositionKey, layoutKey))
                .withTime(layout.getTime())
                .withSubject(compositionKey)
                .withType(type)
                .build());
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
