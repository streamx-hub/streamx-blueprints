package dev.streamx.blueprints.rendering.engine;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;

import dev.streamx.blueprints.data.Renderer;
import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.rendering.engine.converter.PreservedRenderingContext;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.reactive.messaging.GenericPayload;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class RenderingContexts {

  @FromChannel(Channels.Incoming.RENDERING_CONTEXTS)
  Store<PreservedRenderingContext> renderingContextStore;

  @FromChannel(Channels.Incoming.RENDERERS)
  Store<Renderer> renderersStore;

  List<KeyedValue<RenderingContext>> getByRendererKey(String rendererKey) {
    return getPublishedContexts()
        .filter(entry -> rendererKey.equals(entry.value().getRendererKey()))
        .toList();
  }

  List<KeyedValue<RenderingContext>> getByData(String dataKey, String dataType) {
    return getPublishedContexts()
        .filter(entry -> isMatchingData(entry.value(), dataKey, dataType))
        .filter(entry -> hasRenderer(entry.value()))
        .toList();
  }

  boolean hasRenderer(RenderingContext renderingContext) {
    return Optional.ofNullable(renderingContext)
        .map(RenderingContext::getRendererKey)
        .map(key -> renderersStore.getWithMetadata(key))
        .filter(rendererStoreEntry -> PUBLISH.equals(Action.from(rendererStoreEntry.getMetadata())))
        .map(GenericPayload::getPayload)
        .isPresent();
  }

  private Stream<KeyedValue<RenderingContext>> getPublishedContexts() {
    return renderingContextStore.entriesWithMetadata()
        .filter(entry -> PUBLISH.equals(Action.from(entry.value().getMetadata())))
        .map(entry -> new KeyedValue<>(entry.key(), Optional.ofNullable(entry.value().getPayload())
            .map(PreservedRenderingContext::getRenderingContext)
            .orElse(null)))
        .filter(entry -> entry.value() != null);
  }

  static boolean isMatchingData(RenderingContext renderingContext, String dataKey,
      String dataType) {
    try {
      return hasFilter(renderingContext)
          && isMatching(renderingContext.getDataKeyMatchPattern(), dataKey)
          && isMatching(renderingContext.getDataTypeMatchPattern(), dataType);
    } catch (PatternSyntaxException e) {
      // ignore invalid patterns
      return false;
    }
  }

  private static boolean hasFilter(RenderingContext renderingContext) {
    return StringUtils.isNotBlank(renderingContext.getDataKeyMatchPattern())
        || StringUtils.isNotBlank(renderingContext.getDataTypeMatchPattern());
  }

  private static boolean isMatching(String pattern, String value) {
    // accept all values if not patter set in the context
    return StringUtils.isBlank(pattern)
        // otherwise, value must be present and matching the pattern
        || (value != null && value.matches(pattern));
  }

}
