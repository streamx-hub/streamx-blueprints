package dev.streamx.blueprints.rendering.engine.converter;

import dev.streamx.blueprints.data.Data;
import org.apache.avro.specific.AvroGenerated;

/**
 * Designed to keep access to previous version of the {@link Data} in store in case of unpublish
 * using {@link PreservedDataMessageConverter}.
 */
@AvroGenerated
public class PreservedData {

  private Data data;

  private PreservedData() {
    // needed for Avro serialization
  }

  public PreservedData(Data data) {
    this.data = data;
  }

  public Data getData() {
    return data;
  }
}
