package com.streamx.blueprints.web.server.http;

import com.streamx.blueprints.web.server.storage.FileSystemResourceStorage;
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

public class StaticResources {

  @Inject
  FileSystemResourceStorage storage;

  void installRoute(@Observes StartupEvent startupEvent, Router router) {
    String storageRootDirectory = storage.getStorageRootDirectory();
    installSitemapRoute(router, storageRootDirectory);
    installDefaultRoute(router, storageRootDirectory);
  }

  private static void installSitemapRoute(Router router, String storageRootDirectory) {
    router.route("/sitemap.xml").handler(new SitemapHandler(storageRootDirectory));
  }

  private static void installDefaultRoute(Router router, String storageRootDirectory) {
    FileSystemAccess access = getAccessType(storageRootDirectory);
    StaticHandler handler = StaticHandler
        .create(access, storageRootDirectory)
        .setCachingEnabled(false);
    router.route().handler(handler);
  }

  private static FileSystemAccess getAccessType(String storageRootDirectory) {
    return storageRootDirectory.startsWith("/")
        ? FileSystemAccess.ROOT
        : FileSystemAccess.RELATIVE;
  }
}