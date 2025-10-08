package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Renderer;
import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.rendering.engine.converter.PreservedRenderingContext;
import dev.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import dev.streamx.metadata.Properties;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.List;
import org.apache.pulsar.client.api.Schema;
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

  /**
   * This is to force the schema of the rendering context channel and avoid using
   * {@link PreservedRenderingContext} to init the schema.
   */
  @Produces
  @Identifier(Channels.Incoming.RENDERING_CONTEXTS)
  Schema<RenderingContext> renderingContextSchema = Schema.AVRO(RenderingContext.class);

  /**
   * Handles processing of data publication/un-publication to the system. Triggers rendering
   * requests related to the incoming data. See {@link ProcessRenderingRequestFunction}.
   *
   * @param incoming represents data related event
   * @return stream of rendering request events
   */
  @Incoming(Channels.Incoming.DATA)
  @Outgoing(Channels.Outgoing.RENDERING_REQUESTS)
  public Multi<Message<RenderingRequest>> processData(Message<Data> incoming) {
    var dataKey = extractKey(incoming);
    var dataType = Properties.from(incoming).getType().orElse(null);
    var entryStream = List.of(new KeyedValue<>(dataKey, incoming.getPayload()));

    return renderingRequests.getFromDataEntries(incoming,
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
  public Multi<Message<RenderingRequest>> processRenderer(Message<Renderer> incoming) {
    outputGenerator.invalidateCache();

    return renderingRequests.getFromDataStore(incoming,
        renderingContexts.getByRendererKey(extractKey(incoming)));
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
  public Multi<Message<RenderingRequest>> processContext(
      Message<PreservedRenderingContext> incoming) {
    RenderingContext renderingContext = incoming.getPayload().getRenderingContext();
    if (renderingContexts.hasRenderer(renderingContext)) {
      return renderingRequests.getFromDataStore(incoming,
          List.of(new KeyedValue<>(extractKey(incoming), renderingContext)));
    } else {
      // If no corresponding renderer no reason to retrigger data processing because output cannot
      // be rendered anyway.
      incoming.ack();
      return Multi.createFrom().empty();
    }
  }

}
