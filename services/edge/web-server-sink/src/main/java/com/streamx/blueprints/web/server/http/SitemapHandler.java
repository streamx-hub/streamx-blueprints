package com.streamx.blueprints.web.server.http;

import io.smallrye.common.constraint.NotNull;
import io.vertx.core.Handler;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import org.jboss.logging.Logger;

class SitemapHandler implements Handler<RoutingContext> {

  private static final Logger log = Logger.getLogger(SitemapHandler.class);

  private final String storageRootDirectory;

  SitemapHandler(String storageRootDirectory) {
    this.storageRootDirectory = storageRootDirectory;
  }

  @Override
  public void handle(RoutingContext context) {
    HostAndPort hostAndPort = context.request().authority();
    if (hostAndPort == null) {
      context.response().setStatusCode(404).end();
      return;
    }

    String sitemapPath = computeSitemapFilePath(hostAndPort);

    context.response().sendFile(sitemapPath, result -> {
      if (!result.succeeded()) {
        Throwable ex = result.cause();
        log.errorf(ex, "Failed to serve sitemap from %s", sitemapPath);
        context.response().setStatusCode(404).end();
      }
    });
  }

  private String computeSitemapFilePath(@NotNull HostAndPort hostAndPort) {
    String hostAndPortString = hostAndPort.toString().replace(':', '/');
    return "%s/sitemaps/%s/sitemap.xml".formatted(storageRootDirectory, hostAndPortString);
  }
}