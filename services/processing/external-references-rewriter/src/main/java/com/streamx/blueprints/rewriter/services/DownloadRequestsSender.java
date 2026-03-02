package com.streamx.blueprints.rewriter.services;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.rewriter.Channels;
import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.data.ExternalResource;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DownloadRequestsSender {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Channel(Channels.DOWNLOAD_REQUESTS)
  @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 5000)
  @Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
  Emitter<CloudEvent> downloadRequestEmitter;

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

    CloudEvent cloudEvent = CloudEventUtils.eventWithData(
        streamxKey,
        DownloadRequest.DOWNLOAD_REQUEST_EVENT_TYPE,
        downloadRequest
    );

    downloadRequestEmitter.send(cloudEvent);
  }
}