package com.streamx.blueprints.web.delivery.http;

import com.streamx.blueprints.web.delivery.storage.FileSystemResourceStorage;
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
    FileSystemAccess handlerVisibility = storage.getStorageRootDirectory().startsWith("/")
        ? FileSystemAccess.ROOT : FileSystemAccess.RELATIVE;
    router.route()
        .handler(StaticHandler
            .create(handlerVisibility, storage.getStorageRootDirectory())
            .setCachingEnabled(false));
  }
}