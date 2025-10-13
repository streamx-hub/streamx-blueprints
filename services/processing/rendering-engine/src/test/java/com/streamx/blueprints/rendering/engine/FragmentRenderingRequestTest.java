package com.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FragmentRenderingRequestTest extends AbstractRenderingRequestTest {

  public FragmentRenderingRequestTest() {
    super(OutputFormat.FRAGMENT);
  }
}
