package dev.streamx.blueprints.rendering.engine.converter;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.RenderingContext;
import dev.streamx.blueprints.rendering.engine.Channels;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.reactive.messaging.MessageConverter;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Type;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class PreservedRenderingContextMessageConverter implements MessageConverter {

  @FromChannel(Channels.Incoming.RENDERING_CONTEXTS)
  Store<PreservedRenderingContext> renderingContextStore;

  @Override
  public boolean canConvert(Message<?> message, Type type) {
    return type.equals(PreservedRenderingContext.class)
        && (message.getPayload() == null || message.getPayload() instanceof RenderingContext);
  }

  @Override
  public Message<?> convert(Message<?> message, Type type) {
    RenderingContext renderingContext;
    if (Action.UNPUBLISH.equals(extractAction(message))) {
      PreservedRenderingContext entry = renderingContextStore.get(extractKey(message));
      renderingContext = entry == null ? null : entry.getRenderingContext();
    } else {
      Object payload = message.getPayload();
      renderingContext = payload == null ? null : (RenderingContext) payload;
    }
    return message.withPayload(new PreservedRenderingContext(renderingContext));
  }
}
