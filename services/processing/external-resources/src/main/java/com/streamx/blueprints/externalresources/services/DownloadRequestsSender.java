package com.streamx.blueprints.externalresources.services;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.DownloadRequest;
import com.streamx.blueprints.externalresources.Channels;
import com.streamx.blueprints.externalresources.configuration.Configuration;
import com.streamx.blueprints.externalresources.data.ExternalResource;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DownloadRequestsSender {

  @Inject
  Logger log;

  @Inject
  Configuration configuration;

  @Channel(Channels.DOWNLOAD_REQUESTS)
  Emitter<CloudEvent> downloadRequestEmitter;

  public void sendRequest(ExternalResource resource) {
    String absoluteUrl = resource.getAbsoluteUrl();
    String streamxKey = resource.getStreamxKey();
    DownloadRequest downloadRequest = new DownloadRequest(
        absoluteUrl,
        streamxKey,
        configuration.externalPageEmitPayloadType(),
        configuration.externalWebResourceEmitPayloadType(),
        configuration.externalAssetEmitPayloadType()
    );

    log.tracef("Sending download request for %s", absoluteUrl);

    CloudEvent cloudEvent = CloudEventUtils.builderWithJsonData(downloadRequest)
        .withSubject(streamxKey)
        .withType(DownloadRequest.EVENT_TYPE)
        .build();

    downloadRequestEmitter.send(cloudEvent);
  }
}