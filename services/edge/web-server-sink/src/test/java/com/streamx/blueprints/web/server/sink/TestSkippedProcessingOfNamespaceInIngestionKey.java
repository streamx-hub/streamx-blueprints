package com.streamx.blueprints.web.server.sink;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.WebResource;
import com.streamx.blueprints.web.server.Channels;
import com.streamx.blueprints.web.server.Configuration;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class TestSkippedProcessingOfNamespaceInIngestionKey implements HttpAccessTraits {

  @Inject
  @Any
  InMemoryConnector connector;

  @Inject
  Configuration configuration;

  @InjectSpy
  WebServerSink webServerSink;

  @BeforeEach
  void clearRepository() {
    FileUtils.deleteQuietly(new File(configuration.storageRootDirectory()));
  }

  @BeforeEach
  void configureMocks() {
    doReturn(false).when(webServerSink).processNamespaceInIngestionKeys();
  }

  @Test
  void shouldSkipProcessingNamespaceInIngestionKey() {
    // given
    String key = "/resources/jcr:uuid/file.xml";
    WebResource resource = new WebResource("<root />", "resource/xml");
    CloudEvent event = CloudEventUtils.eventWithData(key, WebResource.TYPE_PUBLISHED, resource);

    // when
    connector.source(Channels.RESOURCES).send(event);

    // then
    assertThat(new File(configuration.storageRootDirectory(), key)).exists();
    assertCanAccessViaHttp(key, resource.getContentAsString());
  }
}
