package com.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.rendering.engine.Channels.Incoming;
import com.streamx.blueprints.rendering.engine.Channels.Outgoing;
import com.streamx.blueprints.test.unit.StatefulInMemorySource;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

class BaseInMemoryTest {

  @Inject
  @Any
  InMemoryConnector connector;

  protected StatefulInMemorySource dataSource;
  protected StatefulInMemorySource renderersSource;
  protected StatefulInMemorySource renderingContextsSource;
  protected InMemorySource<CloudEvent> renderingRequestsSource;
  protected InMemorySink<CloudEvent> renderingRequestsSink;

  @BeforeEach
  void init() {
    dataSource = new StatefulInMemorySource(connector,
        Incoming.DATA, Incoming.DATA_STATE);
    renderersSource = new StatefulInMemorySource(connector,
        Incoming.RENDERERS, Incoming.RENDERERS_STATE);
    renderingContextsSource = new StatefulInMemorySource(connector,
        Incoming.RENDERING_CONTEXTS, Incoming.RENDERING_CONTEXTS_STATE);

    renderingRequestsSource = connector.source(Incoming.RENDERING_REQUESTS);

    renderingRequestsSink = connector.sink(Outgoing.RENDERING_REQUESTS);
    renderingRequestsSink.clear();
  }
}
