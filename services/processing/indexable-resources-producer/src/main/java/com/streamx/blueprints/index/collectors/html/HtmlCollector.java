package com.streamx.blueprints.index.collectors.html;

import com.streamx.blueprints.data.Page;
import java.util.Map;

public interface HtmlCollector {

  Map<String, Object> getFacets(Page page);

  Map<String, Object> getFields(Page page);
}
