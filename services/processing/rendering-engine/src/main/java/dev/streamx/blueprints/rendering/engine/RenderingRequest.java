package dev.streamx.blueprints.rendering.engine;

import dev.streamx.blueprints.data.RenderingContext.OutputFormat;
import org.apache.avro.specific.AvroGenerated;

/**
 * This is model dedicated for relay topic used internally by the Rendering Engine to trigger
 * generation of output. Information if the output should be published or unpublished should be
 * passed via {@link dev.streamx.quasar.reactive.messaging.metadata.Action} metadata.
 */
@AvroGenerated
public class RenderingRequest {

  private String dataKey;
  private String rendererKey;
  private String outputKeyTemplate;
  private String outputTypeTemplate;
  private OutputFormat outputFormat;

  private RenderingRequest() {
    // needed for Avro serialization
  }

  public RenderingRequest(String dataKey, String rendererKey, String outputKeyTemplate,
      String outputTypeTemplate, OutputFormat outputFormat) {
    this.dataKey = dataKey;
    this.rendererKey = rendererKey;
    this.outputKeyTemplate = outputKeyTemplate;
    this.outputTypeTemplate = outputTypeTemplate;
    this.outputFormat = outputFormat;
  }

  public String getDataKey() {
    return dataKey;
  }

  public String getRendererKey() {
    return rendererKey;
  }

  public String getOutputKeyTemplate() {
    return outputKeyTemplate;
  }

  public String getOutputTypeTemplate() {
    return outputTypeTemplate;
  }

  public OutputFormat getOutputFormat() {
    return outputFormat;
  }
}
