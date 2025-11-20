package com.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.rendering.engine.converter.PreservedDataStore;
import com.streamx.blueprints.rendering.engine.converter.PreservedRenderingContextStore;
import com.streamx.blueprints.rendering.engine.converter.RendererEventsStore;
import com.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
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
  PreservedDataStore dataStore;

  @Inject
  PreservedRenderingContextStore renderingContextStore;

  @Inject
  RendererEventsStore renderersStore;

  /**
   * Handles processing of data publication/un-publication to the system. Triggers rendering
   * requests related to the incoming data. See {@link ProcessRenderingRequestFunction}.
   *
   * @param incoming represents data related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.DATA)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  // TODO migrate from Message<CloudEvent> to CloudEvent
  //  when https://github.com/smallrye/smallrye-reactive-messaging/issues/3232 is fixed
  public Multi<Message<CloudEvent>> processData(Message<CloudEvent> incoming) {
    CloudEvent dataEvent = incoming.getPayload();
    String eventType = dataEvent.getType();
    String dataKey = CloudEventUtils.getSubject(dataEvent);

    Data data = CloudEventUtils.getData(dataEvent, Data.class);
    String dataType = data == null ? null : data.getType();

    dataStore.register(data, eventType, dataKey);
    var entryStream = Stream.of(new KeyedValue<>(dataKey, data));

    return renderingRequests.getFrom(incoming,
        renderingContexts.getByData(dataKey, dataType), entryStream);
  }

  /**
   * Handles processing of renderer publication/un-publication to the system. Triggers rendering
   * requests related to the incoming renderer. See {@link ProcessRenderingRequestFunction}.
   *
   * @param incoming represents renderer related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.RENDERERS)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  // TODO migrate from Message<CloudEvent> to CloudEvent
  //  when https://github.com/smallrye/smallrye-reactive-messaging/issues/3232 is fixed
  public Multi<Message<CloudEvent>> processRenderer(Message<CloudEvent> incoming) {
    outputGenerator.invalidateCache();
    CloudEvent event = incoming.getPayload();
    String subject = CloudEventUtils.getSubject(event);
    Renderer renderer = CloudEventUtils.getData(event, Renderer.class);
    renderersStore.register(renderer, event.getType(), subject);
    return renderingRequests.getFromDataStore(incoming,
        renderingContexts.getByRendererKey(subject));
  }

  /**
   * Handles processing of rendering context publication/un-publication to the system. Triggers
   * rendering requests related to the incoming context. See
   * {@link ProcessRenderingRequestFunction}.
   *
   * @param incoming represents rendering context related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.RENDERING_CONTEXTS)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  // TODO migrate from Message<CloudEvent> to CloudEvent
  //  when https://github.com/smallrye/smallrye-reactive-messaging/issues/3232 is fixed
  public Multi<Message<CloudEvent>> processContext(Message<CloudEvent> incoming) {
    CloudEvent event = incoming.getPayload();
    RenderingContext renderingContext = registerAndRetrieveFromStore(event);

    if (renderingContexts.hasRenderer(renderingContext)) {
      return renderingRequests.getFromDataStore(incoming,
          List.of(new KeyedValue<>(event.getSubject(), renderingContext)));
    } else {
      // If no corresponding renderer no reason to retrigger data processing because output cannot
      // be rendered anyway.
      incoming.ack();
      return Multi.createFrom().empty();
    }
  }

  private RenderingContext registerAndRetrieveFromStore(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    RenderingContext renderingContext = CloudEventUtils.getData(event, RenderingContext.class);
    renderingContextStore.register(renderingContext, event.getType(), subject);
    return renderingContextStore.get(subject);
  }

}
