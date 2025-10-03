package com.streamx.blueprints.image.optimizer;

import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.reactive.messaging.MessageConverter;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Type;
import java.util.Optional;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class ActionMessageConverter implements MessageConverter {

  @Override
  public boolean canConvert(Message<?> message, Type type) {
    return type == String.class && hasAction(message);
  }

  @Override
  public Message<?> convert(Message<?> message, Type type) {
    return message.withPayload(getAction(message));
  }

  private static boolean hasAction(Message<?> message) {
    return getActionOptional(message).isPresent();
  }

  private static String getAction(Message<?> message) {
    return getActionOptional(message)
        .map(Action::getValue)
        .orElse(null);
  }

  private static Optional<Action> getActionOptional(Message<?> message) {
    return message.getMetadata(Action.class);
  }
}