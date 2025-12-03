package com.streamx.blueprints.opensearch.sink.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

  @Inject
  ObjectMapper objectMapper;

  public Uni<Void> add(String path, IndexableResource resource, String namespace, String type) {
    return Uni.createFrom()
        .item(path)
        .invoke(() -> log.tracev("Indexing resource: {0} with type: {1}", path, type))
        .flatMap(s -> {
          List<Fragment> fragments = resource.getFragmentKeys()
              .stream()
              .map(this::fillFragment)
              .toList();

          DefaultDocument document = new DefaultDocument(
              fragments,
              namespace,
              type,
              resource.getContentAsString()
          );

          return defaultRepository.index(path, document);
        }).onFailure().invoke(e -> log.errorv(e, "Failed to add to index [{0}]", path));
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
    String content = Optional.ofNullable(fragmentResource)
        .filter(ignore -> IndexableResourceFragment.TYPE_PUBLISHED.equals(eventType))
        .map(Resource::getContentAsString)
        .orElse(EMPTY_FRAGMENT);

    Fragment fragment = new Fragment(key, eventTime, content);

    return defaultRepository.updateFragment(fragment)
        .repeat().whilst(this::conflictOccurred)
        .onItem().invoke(() -> log.tracev("Fragment updated: {0}", fragment))
        .toUni()
        .replaceWithVoid()
        .onFailure().invoke(e -> log.errorv(e, "Failed to update fragment {0}", key));
  }

  private boolean conflictOccurred(UpdateResult result) {
    boolean conflictOccurred = !result.failures().isEmpty() || result.versionConflicts() > 0;
    if (conflictOccurred) {
      String serializedFailures = result.failures().stream()
          .map(this::formatJson)
          .collect(Collectors.joining("\n", "\n", "\n"));
      log.debugf("Updating fragments done with failures: %s, versionConflicts: %s. Retrying...",
          serializedFailures, result.versionConflicts());
    }
    return conflictOccurred;
  }

  private String formatJson(JsonNode jsonNode) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
    } catch (JsonProcessingException ex) {
      return jsonNode.toString();
    }
  }
}
