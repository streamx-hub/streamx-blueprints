package com.streamx.blueprints.opensearch.sink.index;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.IndexableResource;
import com.streamx.blueprints.data.IndexableResourceFragment;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.opensearch.sink.index.model.DefaultDocument;
import com.streamx.blueprints.opensearch.sink.index.model.Fragment;
import com.streamx.blueprints.opensearch.sink.opensearch.DefaultRepository;
import com.streamx.blueprints.opensearch.sink.opensearch.DefaultRepository.UpdateResult;
import com.streamx.blueprints.opensearch.sink.store.PublishedIndexableResourceFragmentsStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
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

  @Inject
  PublishedIndexableResourceFragmentsStore indexableResourceFragmentsStore;

  public Uni<Void> add(String path, IndexableResource resource, String namespace, String type) {
    return Uni.createFrom()
        .item(path)
        .invoke(() -> log.tracev("Indexing resource: {0} with type: {1}", path, type))
        .map(s -> {
          var fragmentKeys = Optional.ofNullable(resource.getFragmentKeys()).orElseGet(Set::of);
          var fragmentList = fragmentKeys.stream()
              .map(this::fillFragment)
              .toList();

          var document = new DefaultDocument(
              fragmentList,
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
    var fragmentFromStore = indexableResourceFragmentsStore.get(fragmentKey);
    if (fragmentFromStore != null) {
      return new Fragment(
          fragmentKey,
          fragmentFromStore.eventTime(),
          fragmentFromStore.content()
      );
    } else {
      return new Fragment(
          fragmentKey,
          CloudEventUtils.toOffsetDateTime(0),
          EMPTY_FRAGMENT
      );
    }
  }

  public Uni<Void> delete(String path) {
    return Uni.createFrom().item(path)
        .onItem().invoke(() -> log.tracev("Removing resource from index: {0}", path))
        .flatMap(p -> defaultRepository.deleteFromIndex(p))
        .onFailure().invoke(e -> log.errorv(e, "Failed to remove from index [{0}]", path));
  }

  public Uni<Void> updateFragment(String key, String eventType, OffsetDateTime eventTime,
      IndexableResourceFragment fragmentResource) {
    var content = Optional.ofNullable(fragmentResource)
        .filter(ignore -> IndexableResourceFragment.TYPE_PUBLISHED.equals(eventType))
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
