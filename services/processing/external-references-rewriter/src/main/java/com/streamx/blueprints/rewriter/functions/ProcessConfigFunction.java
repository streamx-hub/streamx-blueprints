package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Config;
import com.streamx.blueprints.rewriter.Channels;
import io.cloudevents.CloudEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProcessConfigFunction {

  @Inject
  Logger log;

  @Incoming(Channels.INCOMING_CONFIGS_STATE)
  public void processConfig(CloudEvent event) {
    Config config;
    try {
      config = CloudEventUtils.getData(event, Config.class);
      if (config != null) {
        config.configMap().forEach(System::setProperty);
      }
    } catch (IllegalStateException e) {
      log.warnf("Failed to read configuration from %s", event.getSubject());
    }
  }

  @Incoming(Channels.INCOMING_CONFIGS)
  public void process(CloudEvent event) {
    // intentionally left blank
  }
}