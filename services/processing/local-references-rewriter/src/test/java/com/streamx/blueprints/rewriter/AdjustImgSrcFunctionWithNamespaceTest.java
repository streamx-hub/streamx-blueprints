package com.streamx.blueprints.rewriter;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.OptimizedAsset;
import io.cloudevents.CloudEvent;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AdjustImgSrcFunctionWithNamespaceTest extends BaseAdjustImgSrcFunctionTest {

  private static final String NAMESPACE = "test-images";

  @Override
  protected CloudEvent createOptimizedAssetPublishEvent(Image image) {
    return CloudEventUtils.eventWithData(
        namespacedSubject(image.optimizedPath),
        OptimizedAsset.TYPE_PUBLISHED,
        new OptimizedAsset(new byte[]{0, 1, 2}, "any", namespacedSubject(image.originalPath))
    );
  }

  @Override
  protected CloudEvent createOptimizedAssetUnpublishEvent(Image image) {
    return CloudEventUtils.eventWithoutData(
        namespacedSubject(image.optimizedPath),
        OptimizedAsset.TYPE_UNPUBLISHED
    );
  }

  private static String namespacedSubject(String rawSubject) {
    return CloudEventUtils.createNamespacedSubject(NAMESPACE, rawSubject);
  }

}
