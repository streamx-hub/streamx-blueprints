package dev.streamx.blueprints.externalresources.functions;

import static dev.streamx.quasar.reactive.messaging.utils.MetadataUtils.extractKey;

import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.Channels;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.compress.utils.FileNameUtils;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessWebResourceFunction {

  @Inject
  Logger log;

  @Inject
  ProcessXmlWebResourceFunction xmlWebResourceFunction;

  @Inject
  ProcessJsonWebResourceFunction jsonWebResourceFunction;

  @Inject
  ProcessHtmlWebResourceFunction htmlWebResourceFunction;

  private Map<String, BaseProcessWebResourceFunction> webResourceFunctionsMap;

  @PostConstruct
  void initWebResourceFunctionsMap() {
    webResourceFunctionsMap = Map.of(
        xmlWebResourceFunction.handledResourcePathSuffix(), xmlWebResourceFunction,
        jsonWebResourceFunction.handledResourcePathSuffix(), jsonWebResourceFunction,
        htmlWebResourceFunction.handledResourcePathSuffix(), htmlWebResourceFunction
    );
  }

  @Incoming(Channels.INCOMING_WEB_RESOURCES)
  @Outgoing(Channels.OUTGOING_WEB_RESOURCES)
  public Uni<Message<WebResource>> processIncomingWebResource(Message<WebResource> message) {
    String key = extractKey(message);
    String extension = getExtensionWithLeadingDot(key);

    return Optional.ofNullable(webResourceFunctionsMap.get(extension))
        .map(function -> function.processIncomingResource(message))
        .orElseGet(() -> relayedMessage(message, key, extension));
  }

  private static String getExtensionWithLeadingDot(String key) {
    return Optional
        .ofNullable(FileNameUtils.getExtension(Path.of(key)))
        .map(ext -> "." + ext)
        .map(String::toLowerCase)
        .orElse("");
  }

  private Uni<Message<WebResource>> relayedMessage(Message<WebResource> message, String key,
      String extension) {
    log.tracef("Skipping processing web resource %s with unhandled extension %s", key, extension);
    message.ack();
    return Uni.createFrom().item(message);
  }

}