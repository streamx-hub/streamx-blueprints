package dev.streamx.blueprints.web.delivery;

import static dev.streamx.blueprints.cloudeventsutils.CloudEventUtils.isPublishingType;
import static dev.streamx.blueprints.cloudeventsutils.CloudEventUtils.isUnpublishingType;

import dev.streamx.blueprints.cloudeventsutils.CloudEventUtils;
import dev.streamx.blueprints.data.TypedBinaryResource;
import dev.streamx.blueprints.web.delivery.storage.FileSystemResourceStorage;
import dev.streamx.content.parser.urlinclude.UrlIncludeReplacer;
import dev.streamx.content.parser.urlinclude.UrlIncludeReplacerFactory;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebDeliverySink {

  public static final String CHANNEL = "resources";

  private UrlIncludeReplacer urlIncludeReplacer;

  @ConfigProperty(name = "streamx.blueprints.web-delivery-service.default-namespace")
  Optional<String> defaultNamespace;

  @ConfigProperty(name = "streamx.url-include-replacement.allowed-types")
  Optional<List<String>> urlReplacerAllowedTypes;

  @Inject
  Logger log;

  @Inject
  Config config;

  @Inject
  FileSystemResourceStorage fileSystemResourceStorage;

  @PostConstruct
  void init() {
    urlIncludeReplacer = UrlIncludeReplacerFactory.create(config);
  }

  @Incoming(CHANNEL)
  public Uni<Void> consume(CloudEvent event) {
    TypedBinaryResource resource;
    try {
      resource = CloudEventUtils.getData(event, TypedBinaryResource.class);
    } catch (IllegalStateException e) {
      log.warnf("Unsupported event: subject %s, type %s", event.getSubject(), event.getType());
      return Uni.createFrom().voidItem();
    }
    return process(resource, event.getSubject(), event.getType(),
        Objects.requireNonNull(event.getTime()).toInstant().toEpochMilli());
  }

  private <T extends TypedBinaryResource> Uni<Void> process(T resource, String subject,
      String type, long eventTime) {
    log.tracef("Storing resource: subject %s, type %s, event time %s", subject, type, eventTime);
    return updateStorage(resource, getPathFrom(subject), type);
  }

  private <T extends TypedBinaryResource> Uni<Void> updateStorage(T resource, String path,
      String type) {
    if (isPublishingType(type)) {
      return fileSystemResourceStorage.add(path, getDataToStore(resource, type));
    }
    if (isUnpublishingType(type)) {
      return fileSystemResourceStorage.delete(path);
    }
    log.tracef("Skipping storing of resource with type {}", type);
    return Uni.createFrom().voidItem();
  }

  private <T extends TypedBinaryResource> byte[] getDataToStore(T resource, String type) {
    ByteBuffer content = resource.getContent();
    if (urlReplacerAllowedTypes.map(types -> types.contains(type)).orElse(false)) {
      return urlIncludeReplacer.replace(content).array();
    } else {
      return content.array();
    }
  }

  private String getPathFrom(String subject) {
    String namespace = CloudEventUtils.getSubjectNamespace(subject)
        .orElse(defaultNamespace.orElse(""));
    return namespace + "/" + CloudEventUtils.getSubjectWithoutNamespace(subject);
  }
}
