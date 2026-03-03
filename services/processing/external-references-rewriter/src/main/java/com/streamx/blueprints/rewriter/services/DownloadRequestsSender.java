package com.streamx.blueprints.rewriter.services;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.rewriter.Channels;
import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import io.cloudevents.CloudEvent;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DownloadRequestsSender {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Inject
  @Channel(Channels.DOWNLOAD_REQUESTS)
  MutinyEmitter<CloudEvent> downloadRequestEmitter;

  /**
   * Sends a standard, non-repeatable download request
   */
  public void sendRequest(ExternalResource resource) {
    String absoluteUrl = resource.getAbsoluteUrl();
    String streamxKey = resource.getStreamxKey();
    DownloadRequest downloadRequest = new DownloadRequest(
        absoluteUrl,
        streamxKey,
        configuration.emittedPageType(),
        configuration.emittedWebResourceType(),
        configuration.emittedAssetType()
    );

    log.tracef("Sending download request for %s", absoluteUrl);

    CloudEvent event = CloudEventUtils.eventWithData(
        streamxKey,
        DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE,
        downloadRequest
    );

    downloadRequestEmitter.sendAndAwait(event);
  }
}