package com.streamx.blueprints.index.facets;

import com.streamx.blueprints.data.Page;
import java.util.Map;

public interface FacetsCollector {

  Map<String, String> getFacets(Page page);
}
