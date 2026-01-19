package com.streamx.blueprints.composition;

import static java.util.Objects.requireNonNull;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Composition;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.state.RepositoryFactory;
import com.streamx.blueprints.state.StateRepository;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class State {

  private StateRepository<Layout> layouts;
  private StateRepository<Composition> compositions;

  @PostConstruct
  void initRepositories() {
    layouts = RepositoryFactory.createRepository(Layout.class, "layouts");
    compositions = RepositoryFactory.createRepository(Composition.class, "compositions");
  }

  @Incoming(Channels.INCOMING_LAYOUTS_STATE)
  public Uni<Void> registerLayout(CloudEvent layout) {
    return registerResource(
        layout,
        layouts,
        Layout.class,
        Layout.TYPE_PUBLISHED,
        Layout.TYPE_UNPUBLISHED
    );
  }

  @Incoming(Channels.INCOMING_COMPOSITIONS_STATE)
  public Uni<Void> registerComposition(CloudEvent composition) {
    return registerResource(
        composition,
        compositions,
        Composition.class,
        Composition.TYPE_PUBLISHED,
        Composition.TYPE_UNPUBLISHED
    );
  }

  private <T extends Resource> Uni<Void> registerResource(CloudEvent event,
      StateRepository<T> repository, Class<T> resourceClass,
      String publishType, String unpublishType) {
    String subject = CloudEventUtils.getSubject(event);
    String eventType = event.getType();
    if (publishType.equals(eventType)) {
      T data = requireNonNull(CloudEventUtils.getData(event, resourceClass));
      repository.put(subject, data);
    } else if (unpublishType.equals(eventType)) {
      repository.remove(subject);
    }
    return Uni.createFrom().voidItem();
  }

  public Layout getLayout(String key) {
    return layouts.get(key);
  }

  public Composition getComposition(String key) {
    return compositions.get(key);
  }

  public Stream<String> getCompositionKeysByLayoutKey(String layoutKey) {
    return compositions.entries()
        .filter(entry -> entry.getValue().getLayoutKey().equals(layoutKey))
        .map(Entry::getKey);
  }

}
