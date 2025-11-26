package com.streamx.blueprints.image.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Asset;
import com.streamx.blueprints.data.OptimizedAsset;
import com.streamx.blueprints.image.generator.OptimizedImagesGeneratorIT.IntegrationTestProfile;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTest;
import com.streamx.blueprints.test.integration.BaseQuarkusIntegrationTestProfile;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@TestProfile(IntegrationTestProfile.class)
public class OptimizedImagesGeneratorIT extends BaseQuarkusIntegrationTest {

  @Test
  void shouldGenerateOptimizedImage() throws IOException {
    // given
    File testImageFile = new File("src/test/resources/ds.png");
    String key = testImageFile.getName();
    Asset asset = new Asset(FileUtils.readFileToByteArray(testImageFile), "test-image");
    CloudEvent sourceEvent = CloudEventUtils.eventWithData(key, Asset.TYPE_PUBLISHED, asset);

    // when
    sendEvent(sourceEvent, Channels.INCOMING_ASSETS);

    // then
    CloudEvent outgoingEvent = waitForResponseEvent(Channels.OPTIMIZED_ASSETS);
    assertOutgoingEvent(outgoingEvent, sourceEvent, asset);
  }

  private static void assertOutgoingEvent(CloudEvent outgoingEvent, CloudEvent sourceEvent,
      Asset sourceAsset) {
    assertThat(outgoingEvent.getId()).isNotEqualTo(sourceEvent.getId());
    assertThat(outgoingEvent.getSource()).asString().isEqualTo("optimized-images-generator");
    assertThat(outgoingEvent.getSubject())
        .isNotEqualTo(sourceEvent.getSubject())
        .endsWith("-optimized.webp");
    assertThat(outgoingEvent.getType()).isEqualTo(OptimizedAsset.TYPE_PUBLISHED);
    assertThat(outgoingEvent.getTime()).isEqualTo(sourceEvent.getTime());
    assertThat(outgoingEvent.getDataContentType()).isEqualTo(sourceEvent.getDataContentType());

    var outgoingResource = CloudEventUtils.getData(outgoingEvent, OptimizedAsset.class);
    assertThat(outgoingResource).isNotNull();
    assertThat(outgoingResource.getType()).isEqualTo(sourceAsset.getType());
    assertThat(outgoingResource.getContent()).isNotEqualTo(sourceAsset.getContent());
    assertThat(outgoingResource.getOriginalPath()).isEqualTo(sourceEvent.getSubject());
  }

  public static class IntegrationTestProfile extends BaseQuarkusIntegrationTestProfile {

    @Override
    protected Map<String, String> getServiceConfigProperties() {
      return Map.of(
          "streamx.blueprints.optimized-images-generator.processed-image-path-pattern", ".*"
      );
    }
  }
}