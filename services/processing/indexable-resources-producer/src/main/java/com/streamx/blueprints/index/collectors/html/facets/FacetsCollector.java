package com.streamx.blueprints.index.collectors.html.facets;

import com.streamx.blueprints.data.Page;
import java.util.Map;

public interface FacetsCollector {

  Map<String, Object> getFacets(Page page);
}
