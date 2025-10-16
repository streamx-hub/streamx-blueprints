package com.streamx.blueprints.opensearch.sink;

import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import io.smallrye.reactive.messaging.MessageConverter;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Type;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class EventTimeMessageConverter implements MessageConverter {

  @Override
  public boolean canConvert(Message<?> message, Type type) {
    return type.equals(Long.class) && message.getMetadata(EventTime.class).isPresent();
  }

  @Override
  public Message<?> convert(Message<?> message, Type type) {
    return message.withPayload(message.getMetadata(EventTime.class).get().getValue());
  }
}
