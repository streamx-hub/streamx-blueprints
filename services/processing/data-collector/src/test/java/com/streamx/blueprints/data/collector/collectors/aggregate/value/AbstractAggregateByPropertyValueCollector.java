package com.streamx.blueprints.data.collector.collectors.aggregate.value;

import static dev.streamx.quasar.reactive.messaging.metadata.Action.PUBLISH;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.streamx.blueprints.data.Data;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.junit.jupiter.api.Disabled;

@Disabled
abstract class AbstractAggregateByPropertyValueCollector {

  public static final String PRODUCT_1_ID = "B072ZLCB3M";
  public static final String PRODUCT_2_ID = "B07TMH6289";
  public static final String PRODUCT_3_ID = "B07DBGJ3TF";
  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private Map<String, String> products = Map.of(
      PRODUCT_1_ID,
      "com/streamx/blueprints/data/collector/collectors/aggregate/value/products1.json",
      PRODUCT_2_ID,
      "com/streamx/blueprints/data/collector/collectors/aggregate/value/products2.json",
      PRODUCT_3_ID,
      "com/streamx/blueprints/data/collector/collectors/aggregate/value/products3.json");

  protected Message<Data> getDataMessage(String productId) throws IOException {
    try (InputStream stream = AbstractAggregateByPropertyValueCollector.class.getClassLoader()
        .getResourceAsStream(products.get(productId))) {
      String content = IOUtils.toString(stream, StandardCharsets.UTF_8);
      String key = "product:" + productId;
      long eventTime = 1L;
      return Message.of(new Data(content),
          Metadata.of(Key.of(key), EventTime.of(eventTime), PUBLISH, Properties.empty()));
    }
  }
}
