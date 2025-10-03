package dev.streamx.blueprints.sitemap;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractAction;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractEventTime;
import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.Page;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import io.smallrye.reactive.messaging.MessageConverter;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.reflect.Type;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class PageEntryMessageConverter implements MessageConverter {

  @Override
  public boolean canConvert(Message<?> message, Type type) {
    return type.equals(PageEntry.class)
        && (message.getPayload() == null || message.getPayload() instanceof Page)
        && metadataExists(message);
  }

  private boolean metadataExists(Message<?> message) {
    return extractEventTime(message) != null
        && extractKey(message) != null
        && extractAction(message) != null;
  }

  @Override
  public Message<PageEntry> convert(Message<?> message, Type type) {
    Action action = extractAction(message);
    String pageName = extractKey(message);
    PageEntry entry = new PageEntry(pageName, Action.PUBLISH.equals(action));

    return message.withPayload(entry);
  }
}
