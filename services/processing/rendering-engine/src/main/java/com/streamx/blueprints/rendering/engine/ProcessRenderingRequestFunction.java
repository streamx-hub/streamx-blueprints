package com.streamx.blueprints.rendering.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.Fragment;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.rendering.engine.converter.PreservedData;
import com.streamx.blueprints.rendering.engine.converter.PreservedDataStore;
import com.streamx.blueprints.rendering.engine.converter.RendererEvent;
import com.streamx.blueprints.rendering.engine.converter.RendererEventsStore;
import com.streamx.blueprints.rendering.engine.generator.GeneratorException;
import com.streamx.blueprints.rendering.engine.generator.OutputGenerator;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.Targeted;
import io.smallrye.reactive.messaging.annotations.Outgoings;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessRenderingRequestFunction {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Inject
  Logger log;

  @Inject
  RendererEventsStore renderersStore;

  @Inject
  PreservedDataStore dataStore;

  @Inject
  OutputGenerator outputGenerator;

  private record ProcessingSettings(
      String outgoingChannel,
      String publishEventType,
      String unpublishEventType,
      BiFunction<String, String, WebResource> resourceConstructor) {

  }

  private Map<OutputFormat, ProcessingSettings> processingSettingsMap;

  @PostConstruct
  void init() {
    processingSettingsMap = Map.of(
        OutputFormat.PAGE,
        new ProcessingSettings(
            Channels.Outgoing.PAGES,
            Page.TYPE_PUBLISHED,
            Page.TYPE_UNPUBLISHED,
            Page::new
        ),

        OutputFormat.FRAGMENT,
        new ProcessingSettings(
            Channels.Outgoing.FRAGMENTS,
            Fragment.TYPE_PUBLISHED,
            Fragment.TYPE_UNPUBLISHED,
            Fragment::new
        )
    );
  }

  @Incoming(Channels.Incoming.RENDERING_REQUESTS)
  @Outgoings({
      @Outgoing(Channels.Outgoing.PAGES),
      @Outgoing(Channels.Outgoing.FRAGMENTS)
  })
  public Targeted process(CloudEvent event) {
    RenderingRequest request = CloudEventUtils.getData(event, RenderingRequest.class);
    String subject = CloudEventUtils.getSubject(event);
    if (request == null) {
      log.warnf("Skipping processing event [%s] - no content", subject);
      return Targeted.from(Map.of());
    }

    PreservedData preservedData = dataStore.get(request.dataKey());
    RendererEvent preservedRenderer = renderersStore.get(request.rendererKey());
    Data data = getValue(preservedData);

    if (ObjectUtils.anyNull(preservedRenderer, data)) {
      log.tracef("Cannot proceed with processing %s, renderer or data is missing", subject);
      return Targeted.from(Map.of());
    }

    Renderer renderer = preservedRenderer.renderer();
    boolean isUnpublish = isAnyUnpublishEvent(
        event.getType(),
        preservedData.eventType(),
        preservedRenderer.eventType()
    );
    if (renderer != null || isUnpublish) {
      return generateAndEmitOutputEvent(isUnpublish, request, data, renderer);
    }
    return Targeted.from(Map.of());
  }

  private Targeted generateAndEmitOutputEvent(boolean isUnpublish, RenderingRequest request,
      Data data,
      Renderer renderer) {
    Map<String, Object> dataValue = readValue(data);
    String outputContent = isUnpublish ? null : generateOutputContent(renderer, dataValue);
    String outputType = isUnpublish ? null : generateKey(request.outputTypeTemplate(), dataValue);
    String key = generateKey(request.outputKeyTemplate(), dataValue);

    ProcessingSettings processingSettings = processingSettingsMap.get(request.outputFormat());
    String eventType = getEventType(processingSettings, isUnpublish);
    WebResource resource = createResource(processingSettings, outputType, outputContent);
    CloudEvent resourceEvent = CloudEventUtils.eventWithData(key, eventType, resource);
    return Targeted.of(processingSettings.outgoingChannel, resourceEvent);
  }

  private String getEventType(ProcessingSettings settings, boolean isUnpublish) {
    return isUnpublish ? settings.unpublishEventType : settings.publishEventType;
  }

  private WebResource createResource(ProcessingSettings settings, String outputType,
      String outputContent) {
    return settings.resourceConstructor.apply(outputContent, outputType);
  }

  private boolean isAnyUnpublishEvent(String... relatedEventTypes) {
    // If any of latest related event types was unpublish then the output is unpublished.
    for (String eventType : relatedEventTypes) {
      if (CloudEventUtils.isUnpublishingType(eventType)) {
        return true;
      }
    }
    return false;
  }

  private String generateKey(String template, Map<String, Object> data) {
    if (template == null) {
      return null;
    }
    try {
      return outputGenerator.generate(template, data);
    } catch (GeneratorException e) {
      throw new IllegalStateException("Error while generating from template " + template, e);
    }
  }

  private String generateOutputContent(Renderer renderer, Map<String, Object> data) {
    if (renderer != null) {
      try {
        return outputGenerator.generate(renderer.getTemplateAsString(), data);
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
      return objectMapper.readValue(data.getContentAsBytes(), new TypeReference<>() {
      });
    } catch (IOException e) {
      throw new IllegalStateException("Cannot parse data", e);
    }
  }
}
