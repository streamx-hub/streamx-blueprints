package com.streamx.blueprints.image.generator;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;

@QuarkusTest
class OptimizeImageFunctionWithNamespaceTest extends BaseOptimizeImageFunctionTest {

  private static final String NAMESPACE = "test-images";

  @Override
  protected String getEventSubjectForAssetFile(File assetFile) {
    return CloudEventUtils.createNamespacedSubject(NAMESPACE, assetFile.getPath());
  }
}
