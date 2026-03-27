package com.streamx.blueprints.cloudevents.utils;

import io.cloudevents.CloudEvent;
import io.quarkus.reactivemessaging.http.runtime.MessageIdProvider;
import org.eclipse.microprofile.reactive.messaging.Message;

// TODO replace with same class provided by streamx-service-mesh after release
public class CloudEventMessageIdProvider implements MessageIdProvider {

  @Override
  public String getMessageId(Message<?> message) {
    if (message.getPayload() instanceof CloudEvent event) {
      return event.getSource() + " " + event.getId();
    }
    return null;
  }
}
