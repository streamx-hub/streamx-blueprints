package dev.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.data.Renderer;
import com.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.rendering.engine.converter.PreservedRenderingContextStore;
import dev.streamx.blueprints.rendering.engine.converter.RendererEvent;
import dev.streamx.blueprints.rendering.engine.converter.RendererEventsStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class RenderingContexts {

  @Inject
  PreservedRenderingContextStore renderingContextStore;

  @Inject
  RendererEventsStore renderersStore;

  List<KeyedValue<RenderingContext>> getByRendererKey(String rendererKey) {
    return getPublishedContexts()
        .filter(entry -> rendererKey.equals(entry.value().rendererKey()))
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
        .map(RenderingContext::rendererKey)
        .map(key -> renderersStore.get(key))
        .filter(rendererEvent -> Renderer.TYPE_PUBLISHED.equals(rendererEvent.eventType()))
        .map(RendererEvent::renderer)
        .isPresent();
  }

  private Stream<KeyedValue<RenderingContext>> getPublishedContexts() {
    return renderingContextStore.getAll().stream()
        .filter(entry -> RenderingContext.TYPE_PUBLISHED.equals(entry.getValue().eventType()))
        .map(entry -> new KeyedValue<>(entry.getKey(), entry.getValue().renderingContext()))
        .filter(entry -> entry.value() != null);
  }

  static boolean isMatchingData(RenderingContext renderingContext, String dataKey,
      String dataType) {
    try {
      return hasFilter(renderingContext)
          && isMatching(renderingContext.dataKeyMatchPattern(), dataKey)
          && isMatching(renderingContext.dataTypeMatchPattern(), dataType);
    } catch (PatternSyntaxException e) {
      // ignore invalid patterns
      return false;
    }
  }

  private static boolean hasFilter(RenderingContext renderingContext) {
    return StringUtils.isNotBlank(renderingContext.dataKeyMatchPattern())
        || StringUtils.isNotBlank(renderingContext.dataTypeMatchPattern());
  }

  private static boolean isMatching(String pattern, String value) {
    // accept all values if not patter set in the context
    return StringUtils.isBlank(pattern)
        // otherwise, value must be present and matching the pattern
        || (value != null && value.matches(pattern));
  }

}
