package com.streamx.blueprints.index.facets.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.Configuration;
import com.streamx.blueprints.index.facets.FacetsCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@ApplicationScoped
public class MetadataFacetsCollector implements FacetsCollector {

  @Inject
  Configuration configuration;

  public Map<String, String> getFacets(Page page) {
    if (!configuration.includeFacets()) {
      return Collections.emptyMap();
    }

    Map<String, String> facets = new HashMap<>();
    Document doc = Jsoup.parse(page.getContentAsString());

    Elements metas = doc.select("meta[property^=facets:]");

    for (Element meta : metas) {
      String key = meta.attr("property").substring("facets:".length());
      String value = meta.attr("content");

      facets.put(normalizeKey(key), value);
    }
    return facets;
  }

  private String normalizeKey(String key) {
    return key.trim()
        .toLowerCase()
        .replace("-", "_");
  }
}
