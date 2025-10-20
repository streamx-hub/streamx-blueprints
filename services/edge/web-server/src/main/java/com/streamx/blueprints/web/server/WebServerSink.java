package com.streamx.blueprints.web.server;

import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.isPublishingType;
import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.isUnpublishingType;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.web.server.storage.FileSystemResourceStorage;
import com.streamx.content.parser.urlinclude.UrlIncludeReplacer;
import com.streamx.content.parser.urlinclude.UrlIncludeReplacerFactory;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebServerSink {

  public static final String CHANNEL = "resources";
  private static final String INDEX_HTML_SUFFIX = "index.html";

  private UrlIncludeReplacer urlIncludeReplacer;

  @ConfigProperty(name = "streamx.blueprints.web.default-namespace")
  Optional<String> defaultNamespace;

  @ConfigProperty(name = "streamx.blueprints.web.html-resource.types")
  Optional<List<String>> htmlResourceTypes;

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
    Resource resource;
    try {
      resource = CloudEventUtils.getData(event, Resource.class);
    } catch (IllegalStateException e) {
      log.warnf("Unsupported event: subject %s, type %s", event.getSubject(), event.getType());
      return Uni.createFrom().voidItem();
    }
    return process(resource, event.getSubject(), event.getType(),
        Objects.requireNonNull(event.getTime()).toInstant().toEpochMilli());
  }

  private <T extends Resource> Uni<Void> process(T resource, String subject,
      String type, long eventTime) {
    boolean isHtmlResource = htmlResourceTypes.map(types -> types.contains(type))
        .orElse(false);
    String path = getPathFrom(subject, isHtmlResource);
    log.tracef("Storing resource: subject %s, type %s, event time %s under path %s", subject, type,
        eventTime, path);
    return updateStorage(resource, path, type, isHtmlResource);
  }

  private <T extends Resource> Uni<Void> updateStorage(T resource, String path,
      String type, boolean isHtmlResource) {
    if (isPublishingType(type)) {
      return fileSystemResourceStorage.add(path, getDataToStore(resource, isHtmlResource));
    }
    if (isUnpublishingType(type)) {
      return fileSystemResourceStorage.delete(path);
    }
    log.tracef("Skipping storing of resource with type {}", type);
    return Uni.createFrom().voidItem();
  }

  private <T extends Resource> byte[] getDataToStore(T resource, boolean isHtmlResource) {
    ByteBuffer content = resource.getContent();
    if (isHtmlResource) {
      return urlIncludeReplacer.replace(content).array();
    } else {
      return content.array();
    }
  }

  private String getPathFrom(String subject, boolean isHtmlResource) {
    String namespace = CloudEventUtils.getSubjectNamespace(subject)
        .orElse(defaultNamespace.orElse(""));
    String path = namespace + "/" + CloudEventUtils.getSubjectWithoutNamespace(subject);
    return isHtmlResource ? computeHtmlResourcePath(path) : path;
  }

  static String computeHtmlResourcePath(String path) {
    if (path.endsWith("/")) {
      return path + INDEX_HTML_SUFFIX;
    }
    if (FilenameUtils.getExtension(path).isEmpty()) {
      return path + "/" + INDEX_HTML_SUFFIX;
    }
    return path;
  }
}
