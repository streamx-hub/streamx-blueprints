package com.streamx.blueprints.opensearch.sink.index;

import static com.streamx.blueprints.opensearch.sink.FragmentSearchDeliveryServiceSink.CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS;

import com.streamx.blueprints.opensearch.sink.index.model.DefaultDocument;
import com.streamx.blueprints.opensearch.sink.index.model.Fragment;
import com.streamx.blueprints.opensearch.sink.index.model.SearchIndexStorageException;
import com.streamx.blueprints.opensearch.sink.opensearch.DefaultRepository;
import com.streamx.blueprints.opensearch.sink.opensearch.DefaultRepository.UpdateResult;
import dev.streamx.blueprints.data.IndexableResource;
import dev.streamx.blueprints.data.IndexableResourceFragment;
import dev.streamx.blueprints.data.Resource;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DefaultIndexUpdater {

  private static final String EMPTY_FRAGMENT = "{}";

  @Inject
  Logger log;

  @Inject
  DefaultRepository defaultRepository;

  @FromChannel(CHANNEL_INDEXABLE_RESOURCE_FRAGMENTS)
  Store<IndexableResourceFragment> indexableResourceFragmentsStore;

  public Uni<Void> add(String path, IndexableResource resource, String namespace, String type) {
    return Uni.createFrom()
        .item(path)
        .invoke(() -> log.tracev("Indexing resource: {0} with type: {1}", path, type))
        .invoke(p -> {
          validatePath(p);
          validateResource(resource);
        })
        .map(s -> {
          var fragmentKeys = Optional.ofNullable(resource.getFragmentKeys()).orElseGet(Set::of);
          var fragmentMap = fragmentKeys.stream()
              .map(this::fillFragment)
              .toList();

          var document = new DefaultDocument(
              fragmentMap,
              namespace,
              type,
              resource.getContentAsString()
          );

          return new SimpleImmutableEntry<>(path, document);
        })
        .flatMap(p -> defaultRepository.index(p.getKey(), p.getValue()))
        .onFailure().invoke(e -> log.errorv(e, "Failed to add to index [{0}]", path));
  }

  private Fragment fillFragment(String fragmentKey) {
    var fragmentFromStore = indexableResourceFragmentsStore.getWithMetadata(fragmentKey);
    var eventTime = Optional.ofNullable(fragmentFromStore)
        .map(GenericPayload::getMetadata)
        .map(EventTime::from)
        .map(EventTime::getValue)
        .orElse(0L);

    var payload = Optional.ofNullable(fragmentFromStore)
        .filter(genericPayload -> Action.PUBLISH.equals(Action.from(genericPayload.getMetadata())))
        .map(GenericPayload::getPayload)
        .map(Resource::getContentAsString)
        .orElse(EMPTY_FRAGMENT);

    return new Fragment(
        fragmentKey,
        eventTime,
        payload
    );
  }

  public Uni<Void> delete(String path) {
    return Uni.createFrom().item(path)
        .onItem().invoke(() -> log.tracev("Removing resource from index: {0}", path))
        .invoke(this::validatePath)
        .flatMap(p -> defaultRepository.deleteFromIndex(p))
        .onFailure().invoke(e -> log.errorv(e, "Failed to remove from index [{0}]", path));
  }

  private void validatePath(String path) {
    if (path == null || path.isBlank()) {
      throw new SearchIndexStorageException("Invalid resource path: %s".formatted(path));
    }
  }

  private void validateResource(IndexableResource resource) {
    if (resource == null) {
      throw new SearchIndexStorageException("Resource can't be null!");
    }
  }

  public Uni<Void> updateFragment(String key, Action action, long eventTime,
      IndexableResourceFragment fragmentResource) {
    var content = Optional.ofNullable(fragmentResource)
        .filter(ignore -> Action.PUBLISH.equals(action))
        .map(Resource::getContentAsString)
        .orElse(EMPTY_FRAGMENT);

    var fragment = new Fragment(key, eventTime, content);

    var updateFragmentsHandlingConflicts = defaultRepository.updateFragments(fragment)
        .repeat().whilst(this::conflictOccurred)
        .onItem().invoke(() -> log.tracev("Fragment updated: {0}", fragment))
        .toUni();

    return defaultRepository.refresh()
        .flatMap((ignore) -> updateFragmentsHandlingConflicts)
        .replaceWithVoid()
        .onFailure().invoke(e -> log.errorv(e, "Failed to update fragment {0}", key));
  }

  private boolean conflictOccurred(UpdateResult updateResult) {
    boolean result = (!updateResult.failures().isEmpty()) || updateResult.versionConflicts() > 0;
    if (result) {
      log.debugf("Updating fragments done with failures: %d, versionConflicts: %s. Retrying...",
          updateResult.failures().size(), updateResult.versionConflicts());
    }
    return result;
  }
}
