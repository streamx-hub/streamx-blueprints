package dev.streamx.blueprints.rendering.engine.converter;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.Data;
import dev.streamx.blueprints.rendering.engine.Channels;
import dev.streamx.quasar.reactive.messaging.Store;
import dev.streamx.quasar.reactive.messaging.annotations.FromChannel;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.reactive.messaging.MessageConverter;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Type;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class PreservedDataMessageConverter implements MessageConverter {

  @FromChannel(Channels.Incoming.DATA)
  Store<PreservedData> dataStore;

  @Override
  public boolean canConvert(Message<?> message, Type type) {
    return type.equals(PreservedData.class)
        && (message.getPayload() == null || message.getPayload() instanceof Data);
  }

  @Override
  public Message<?> convert(Message<?> message, Type type) {
    Action action = extractAction(message);
    Data data;
    if (Action.UNPUBLISH.equals(action)) {
      PreservedData preservedData = dataStore.get(extractKey(message));
      data = preservedData == null ? null : preservedData.getData();
    } else {
      Object payload = message.getPayload();
      data = payload == null ? null : (Data) payload;
    }
    return message.withPayload(new PreservedData(data));
  }
}
