package dev.streamx.blueprints.rendering.engine;

import com.streamx.blueprints.data.RenderingContext.OutputFormat;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PageRenderingRequestTest extends AbstractRenderingRequestTest {

  public PageRenderingRequestTest() {
    super(OutputFormat.PAGE);
  }
}
