package com.streamx.blueprints.index.collectors.html.fields;

import com.streamx.blueprints.data.Page;
import java.util.Map;

public interface FieldsCollector {

  Map<String, String> getFields(Page page);
}
