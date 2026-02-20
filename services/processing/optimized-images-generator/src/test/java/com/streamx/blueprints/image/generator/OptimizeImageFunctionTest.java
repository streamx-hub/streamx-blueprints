package com.streamx.blueprints.image.generator;

import io.quarkus.test.junit.QuarkusTest;
import java.io.File;

@QuarkusTest
class OptimizeImageFunctionTest extends BaseOptimizeImageFunctionTest {

  @Override
  protected String getEventSubjectForAssetFile(File assetFile) {
    return assetFile.getPath();
  }
}
