package com.streamx.blueprints.web.server.sink;

import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.isPublishingType;
import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.isUnpublishingType;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.web.server.Channels;
import com.streamx.blueprints.web.server.Configuration;
import com.streamx.blueprints.web.server.storage.FileSystemResourceStorage;
import com.streamx.content.parser.urlinclude.UrlIncludeReplacer;
import com.streamx.content.parser.urlinclude.UrlIncludeReplacerFactory;
import io.cloudevents.CloudEvent;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WebServerSink {

  private static final String INDEX_HTML_SUFFIX = "index.html";

  private UrlIncludeReplacer urlIncludeReplacer;
  private Set<String> htmlResourceTypes;
  private String defaultNamespace;

  @Inject
  Logger log;

  @Inject
  Config config;

  @Inject
  Configuration configuration;

  @Inject
  FileSystemResourceStorage fileSystemResourceStorage;

  @PostConstruct
  void init() {
    urlIncludeReplacer = UrlIncludeReplacerFactory.create(config);
    htmlResourceTypes = configuration.htmlResourceTypes().orElseGet(Collections::emptySet);
    defaultNamespace = configuration.defaultNamespace().orElse("");
  }

  @Incoming(Channels.RESOURCES)
  public Uni<Void> consume(CloudEvent event) {
    String subject = CloudEventUtils.getSubject(event);
    Resource resource;
    try {
      resource = CloudEventUtils.getDataSkippingUnknownProperties(event, Resource.class);
    } catch (IllegalStateException e) {
      log.warnf(e, "Unsupported event: subject %s, type %s", subject, event.getType());
      return Uni.createFrom().voidItem();
    }
    return process(resource, subject, event.getType(),
        Objects.requireNonNull(event.getTime()).toInstant().toEpochMilli());
  }

  private <T extends Resource> Uni<Void> process(T resource, String subject,
      String type, long eventTime) {
    boolean isHtmlResource = htmlResourceTypes.contains(type);
    String path = getPathFrom(subject, isHtmlResource);
    log.tracef("Storing %s resource: subject %s, type %s, event time %s under path %s",
        (isHtmlResource ? "HTML" : "non-HTML"), subject, type, eventTime, path);
    return updateStorage(resource, path, type, isHtmlResource);
  }

  private <T extends Resource> Uni<Void> updateStorage(T resource, String path,
      String type, boolean isHtmlResource) {
    if (isPublishingType(type)) {
      byte[] dataToStore = getDataToStore(resource, isHtmlResource);
      if (dataToStore == null) {
        log.warnf("No data to store for %s", path);
        return Uni.createFrom().voidItem();
      }
      return fileSystemResourceStorage.add(path, dataToStore);
    }
    if (isUnpublishingType(type)) {
      return fileSystemResourceStorage.delete(path);
    }
    log.tracef("Skipping storing of resource with type {}", type);
    return Uni.createFrom().voidItem();
  }

  private <T extends Resource> byte[] getDataToStore(T resource, boolean isHtmlResource) {
    if (isHtmlResource) {
      return urlIncludeReplacer.replace(resource.getContent()).array();
    } else {
      return resource.getContentAsBytes();
    }
  }

  private String getPathFrom(String subject, boolean isHtmlResource) {
    String namespace = CloudEventUtils.getSubjectNamespace(subject).orElse(defaultNamespace);
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
