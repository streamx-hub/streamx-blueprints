package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;
import static dev.streamx.quasar.reactive.messaging.metadata.Action.UNPUBLISH;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.data.Fragment;
import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.Renderer;
import dev.streamx.blueprints.data.RenderingContext.OutputFormat;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.blueprints.rendering.engine.converter.PreservedData;
import dev.streamx.blueprints.rendering.engine.generator.GeneratorException;
import dev.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.reactive.messaging.GenericPayload;
import io.smallrye.reactive.messaging.Targeted;
import io.smallrye.reactive.messaging.annotations.Outgoings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.apache.pulsar.client.api.Schema;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

/**
 * Process rendering requests by generation of output payload and selectively dispatches to one of
 * output channels. Processing is split into 2 steps using internal channel. First step is
 * preparation of the output, second step is dispatching the output to proper outgoing channel. The
 * split is needed as the {@link Targeted} used in the dispatching does not allow to set metadata
 * (TargetedMessages could be used, but it's better to avoid ack handling).
 */
@ApplicationScoped
public class ProcessRenderingRequestFunction {

  private static final String INTERNAL_RENDERING_REQUEST_PROCESSING_CHANNEL =
      "internal-rendering-request-processing-channel";

  @FromChannel(Channels.Incoming.RENDERERS)
  Store<Renderer> renderersStore;

  @FromChannel(Channels.Incoming.DATA)
  Store<PreservedData> dataStore;

  @Inject
  OutputGenerator outputGenerator;

  @Produces
  @Identifier(Channels.Outgoing.PAGES)
  Schema<Page> pageSchema = Schema.AVRO(Page.class);

  @Produces
  @Identifier(Channels.Outgoing.FRAGMENTS)
  Schema<Fragment> fragmentSchema = Schema.AVRO(Fragment.class);

  private final Map<OutputFormat, String> outgoingChannelsByOutputType = Map.of(
      OutputFormat.PAGE, Channels.Outgoing.PAGES,
      OutputFormat.FRAGMENT, Channels.Outgoing.FRAGMENTS
  );

  @Incoming(Channels.Incoming.RENDERING_REQUESTS)
  @Outgoing(INTERNAL_RENDERING_REQUEST_PROCESSING_CHANNEL)
  public GenericPayload<Resource> process(RenderingRequest request, Action requestAction) {
    GenericPayload<PreservedData> data = dataStore.getWithMetadata(request.getDataKey());
    GenericPayload<Renderer> renderer = renderersStore.getWithMetadata(request.getRendererKey());
    if (renderer != null && getValue(data) != null) {
      Action outputAction = getOutputAction(requestAction,
          Action.from(data.getMetadata()),
          Action.from(renderer.getMetadata()));
      if (renderer.getPayload() != null || !PUBLISH.equals(outputAction)) {
        return getOutput(outputAction, request.getOutputKeyTemplate(),
            request.getOutputTypeTemplate(), request.getOutputFormat(),
            getValue(data), renderer.getPayload());
      }
    }
    return null; // skip further processing
  }

  @Incoming(INTERNAL_RENDERING_REQUEST_PROCESSING_CHANNEL)
  @Outgoings({
      @Outgoing(Channels.Outgoing.PAGES),
      @Outgoing(Channels.Outgoing.FRAGMENTS)
  })
  public Targeted dispatchOutput(Resource resource, OutputFormat outputFormat) {
    String outgoingChannel = outgoingChannelsByOutputType.get(outputFormat);
    if (outgoingChannel != null) {
      // Remove NullableTargeted when
      // https://github.com/smallrye/smallrye-reactive-messaging/issues/2687 is fixed
      return new NullableTargeted().with(outgoingChannel, resource);
    }
    return new NullableTargeted();
  }

  private GenericPayload<Resource> getOutput(Action outputAction, String outputKeyTemplate,
      String outputTypeTemplate, OutputFormat outputFormat, Data data, Renderer renderer) {
    Map<String, Object> dataValue = readValue(data);
    Resource output = PUBLISH.equals(outputAction) ? generateOutput(renderer, dataValue) : null;
    String outputType =
        PUBLISH.equals(outputAction) ? generate(outputTypeTemplate, dataValue) : null;
    return GenericPayload.of(output, Metadata.of(
        Key.of(generate(outputKeyTemplate, dataValue)),
        outputAction,
        Properties.empty().withType(outputType),
        outputFormat // used to dispatch the output to outgoing channels,
    ));
  }

  private Action getOutputAction(Action... relatedActions) {
    // If any of latest related actions was unpublish then the output is unpublished.
    for (Action action : relatedActions) {
      if (UNPUBLISH.equals(action)) {
        return UNPUBLISH;
      }
    }
    return PUBLISH;
  }

  private String generate(String template, Map<String, Object> data) {
    if (template == null) {
      return null;
    }
    try {
      return new String(outputGenerator.generate(template, data), StandardCharsets.UTF_8);
    } catch (GeneratorException e) {
      throw new IllegalStateException("Error while generating from template " + template, e);
    }
  }

  private Resource generateOutput(Renderer renderer, Map<String, Object> data) {
    if (renderer != null) {
      try {
        byte[] output = outputGenerator.generate(
            new String(renderer.getTemplate().array(), StandardCharsets.UTF_8), data);
        return new Resource(output);
      } catch (GeneratorException e) {
        throw new RuntimeException("Error while generating content", e);
      }
    }
    return null;
  }

  private Data getValue(GenericPayload<PreservedData> data) {
    return Optional.ofNullable(data)
        .map(GenericPayload::getPayload)
        .map(PreservedData::getData)
        .orElse(null);
  }

  private Map<String, Object> readValue(Data data) {
    try {
      return new ObjectMapper().readValue(data.getContent().array(), new TypeReference<>() {
      });
    } catch (IOException e) {
      throw new IllegalStateException("Cannot parse data", e);
    }
  }
}
