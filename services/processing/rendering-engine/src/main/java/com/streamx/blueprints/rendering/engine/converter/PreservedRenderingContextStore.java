package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.RenderingContext;
import com.streamx.blueprints.rendering.engine.Channels;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class PreservedRenderingContextStore extends BaseStore<PreservedRenderingContext> {

  @PostConstruct
  void initRepository() {
    initRepository("preserved-rendering-contexts", PreservedRenderingContext.class);
  }

  @Incoming(Channels.Incoming.RENDERING_CONTEXTS_STATE)
  void register(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    RenderingContext renderingContext = CloudEventUtils.getData(event, RenderingContext.class);
    if (RenderingContext.TYPE_UNPUBLISHED.equals(eventType)) {
      PreservedRenderingContext preserved = store.get(subject);
      if (preserved == null) {
        store.remove(subject);
      } else {
        store.put(subject, new PreservedRenderingContext(preserved.renderingContext(), eventType));
      }
    } else if (RenderingContext.TYPE_PUBLISHED.equals(eventType)) {
      store.put(subject, new PreservedRenderingContext(renderingContext, eventType));
    }
  }

  public RenderingContext get(String key) {
    return Optional.ofNullable(store.get(key))
        .map(PreservedRenderingContext::renderingContext)
        .orElse(null);
  }

  public Stream<Entry<String, PreservedRenderingContext>> getAll() {
    return store.entries();
  }
}
