package com.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.rendering.engine.converter.PreservedRenderingContextStore;
import com.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

@ApplicationScoped
public class ProcessTriggersFunctions {

  @Inject
  RenderingRequests renderingRequests;

  @Inject
  RenderingContexts renderingContexts;

  @Inject
  OutputGenerator outputGenerator;

  @Inject
  PreservedRenderingContextStore renderingContextStore;

  /**
   * Handles processing of data publication/un-publication to the system. Triggers rendering
   * requests related to the incoming data. See {@link ProcessRenderingRequestFunction}.
   *
   * @param dataEvent represents data related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.DATA)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  public Multi<CloudEvent> processData(CloudEvent dataEvent) {
    String dataKey = CloudEventUtils.getSubject(dataEvent);

    Data data = CloudEventUtils.getData(dataEvent, Data.class);
    String dataType = data == null ? null : data.getType();

    var entryStream = Stream.of(new KeyedValue<>(dataKey, data));

    return renderingRequests.getFrom(dataEvent,
        renderingContexts.getByData(dataKey, dataType), entryStream);
  }

  /**
   * Handles processing of renderer publication/un-publication to the system. Triggers rendering
   * requests related to the incoming renderer. See {@link ProcessRenderingRequestFunction}.
   *
   * @param event represents renderer related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.RENDERERS)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  public Multi<CloudEvent> processRenderer(CloudEvent event) {
    outputGenerator.invalidateCache();
    String subject = CloudEventUtils.getSubject(event);
    return renderingRequests.getFromDataStore(event,
        renderingContexts.getByRendererKey(subject));
  }

  /**
   * Handles processing of rendering context publication/un-publication to the system. Triggers
   * rendering requests related to the incoming context. See
   * {@link ProcessRenderingRequestFunction}.
   *
   * @param event represents rendering context related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.RENDERING_CONTEXTS)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
  public Multi<CloudEvent> processContext(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    RenderingContext renderingContext = renderingContextStore.get(subject);
    if (renderingContexts.hasRenderer(renderingContext)) {
      return renderingRequests.getFromDataStore(event,
          List.of(new KeyedValue<>(event.getSubject(), renderingContext)));
    } else {
      // If no corresponding renderer no reason to retrigger data processing because output cannot
      // be rendered anyway.
      return Multi.createFrom().empty();
    }
  }

}
