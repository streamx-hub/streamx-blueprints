package com.streamx.blueprints.rendering.engine.converter;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.rendering.engine.Channels;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class RendererEventsStore extends BaseStore<RendererEvent> {

  @PostConstruct
  void initRepository() {
    initRepository("renderers", RendererEvent.class);
  }

  @Incoming(Channels.Incoming.RENDERERS_STATE)
  void register(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    Renderer renderer = CloudEventUtils.getData(event, Renderer.class);
    store.put(subject, new RendererEvent(renderer, eventType));
  }

  public RendererEvent get(String key) {
    return store.get(key);
  }
}
