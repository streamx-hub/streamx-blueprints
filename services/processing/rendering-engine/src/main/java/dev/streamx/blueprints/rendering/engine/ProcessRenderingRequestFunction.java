package dev.streamx.blueprints.rendering.engine;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import com.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.rendering.engine.converter.PreservedData;
import dev.streamx.blueprints.rendering.engine.converter.PreservedDataStore;
import dev.streamx.blueprints.rendering.engine.converter.RendererEvent;
import dev.streamx.blueprints.rendering.engine.converter.RendererEventsStore;
import dev.streamx.blueprints.rendering.engine.generator.GeneratorException;
import dev.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ProcessRenderingRequestFunction {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  RendererEventsStore renderersStore;

  @Inject
  PreservedDataStore dataStore;

  @Inject
  OutputGenerator outputGenerator;

  @Channel(Channels.Outgoing.PAGES)
  Emitter<CloudEvent> pagesEmitter;

  @Channel(Channels.Outgoing.FRAGMENTS)
  Emitter<CloudEvent> fragmentsEmitter;

  private record ProcessingSettings(
      String publishEventType,
      String unpublishEventType,
      BiFunction<byte[], String, WebResource> resourceConstructor,
      Emitter<CloudEvent> emitter) {

  }

  private Map<OutputFormat, ProcessingSettings> processingSettingsMap;

  @PostConstruct
  void init() {
    processingSettingsMap = Map.of(
        OutputFormat.PAGE,
        new ProcessingSettings(
            Page.TYPE_PUBLISHED,
            Page.TYPE_UNPUBLISHED,
            Page::new,
            pagesEmitter
        ),

        OutputFormat.FRAGMENT,
        new ProcessingSettings(
            Fragment.TYPE_PUBLISHED,
            Fragment.TYPE_UNPUBLISHED,
            Fragment::new,
            fragmentsEmitter
        )
    );
  }

  @Incoming(Channels.Incoming.RENDERING_REQUESTS)
  public void process(CloudEvent event) {
    RenderingRequest request = CloudEventUtils.getDataOrThrow(event, RenderingRequest.class);

    PreservedData preservedData = dataStore.get(request.dataKey());
    RendererEvent renderer = renderersStore.get(request.rendererKey());
    Data data = getValue(preservedData);
    if (renderer != null && data != null) {
      boolean isPublish = areAllPublishEvents(event.getType(), preservedData.eventType(),
          renderer.eventType());
      if (renderer.renderer() != null || !isPublish) {
        generateAndEmitOutputEvent(isPublish, request, data, renderer.renderer());
      }
    }
  }

  private void generateAndEmitOutputEvent(boolean isPublish, RenderingRequest request, Data data,
      Renderer renderer) {
    Map<String, Object> dataValue = readValue(data);
    byte[] outputContent = isPublish ? generateOutputContent(renderer, dataValue) : null;
    String outputType = isPublish ? generateKey(request.outputTypeTemplate(), dataValue) : null;
    String key = generateKey(request.outputKeyTemplate(), dataValue);

    ProcessingSettings processingSettings = processingSettingsMap.get(request.outputFormat());
    String eventType = getEventType(processingSettings, isPublish);
    WebResource resource = createResource(processingSettings, outputType, outputContent);
    CloudEvent resourceEvent = CloudEventUtils.eventWithData(resource, eventType, key);
    emit(processingSettings, resourceEvent);
  }

  private String getEventType(ProcessingSettings settings, boolean isPublish) {
    return isPublish ? settings.publishEventType : settings.unpublishEventType;
  }

  private WebResource createResource(ProcessingSettings settings, String outputType,
      byte[] outputContent) {
    return settings.resourceConstructor.apply(outputContent, outputType);
  }

  private void emit(ProcessingSettings settings, CloudEvent resourceEvent) {
    settings.emitter.send(resourceEvent);
  }

  private boolean areAllPublishEvents(String... relatedEventTypes) {
    // If any of latest related event types was unpublish then the output is unpublished.
    for (String eventType : relatedEventTypes) {
      if (CloudEventUtils.isUnpublishingType(eventType)) {
        return false;
      }
    }
    return true;
  }

  private String generateKey(String template, Map<String, Object> data) {
    if (template == null) {
      return null;
    }
    try {
      return new String(outputGenerator.generate(template, data), UTF_8);
    } catch (GeneratorException e) {
      throw new IllegalStateException("Error while generating from template " + template, e);
    }
  }

  private byte[] generateOutputContent(Renderer renderer, Map<String, Object> data) {
    if (renderer != null) {
      try {
        return outputGenerator.generate(new String(renderer.template().array(), UTF_8), data);
      } catch (GeneratorException e) {
        throw new RuntimeException("Error while generating content", e);
      }
    }
    return null;
  }

  private Data getValue(PreservedData data) {
    return Optional.ofNullable(data)
        .map(PreservedData::data)
        .orElse(null);
  }

  private Map<String, Object> readValue(Data data) {
    try {
      return objectMapper.readValue(data.getContent().array(), new TypeReference<>() {
      });
    } catch (IOException e) {
      throw new IllegalStateException("Cannot parse data", e);
    }
  }
}
