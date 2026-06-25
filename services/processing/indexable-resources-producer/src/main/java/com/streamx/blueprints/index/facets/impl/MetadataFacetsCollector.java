package com.streamx.blueprints.index.facets.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.Configuration;
import com.streamx.blueprints.index.facets.FacetsCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@ApplicationScoped
public class MetadataFacetsCollector implements FacetsCollector {

  @Inject
  Configuration configuration;

  public Map<String, String> getFacets(Page page) {
    if (!configuration.includeFacets() || !isPropertyConfigured()) {
      return Collections.emptyMap();
    }
    return collectMetadataFacets(page);
  }

  private Map<String, String> collectMetadataFacets(Page page) {
    Document doc = Jsoup.parse(page.getContentAsString());
    Elements metas = doc.select(configuration.metadata().selector().get());

    Map<String, String> facets = new HashMap<>();
    for (Element meta : metas) {
      String key = findFirst(meta, configuration.metadata().keys().get());
      String value = findFirst(meta, configuration.metadata().values().get());

      if (key != null) {
        facets.put(normalizeKey(getKey(key, getKeyDelimiter())), value);
      }
    }
    return facets;
  }

  private String getKeyDelimiter() {
    return configuration.metadata().keyDelimiter().orElse(null);
  }

  private String getKey(String key, String delimiter) {
    if (StringUtils.isBlank(delimiter)) {
      return key;
    }
    String[] parts = key.split(Pattern.quote(delimiter), 2);
    return parts.length > 1 ? parts[1] : key;
  }

  private String findFirst(Element element, List<String> attributesNames) {
    return attributesNames.stream()
        .filter(element::hasAttr)
        .map(element::attr)
        .findFirst()
        .orElse(null);
  }

  private String normalizeKey(String key) {
    return key.trim()
        .toLowerCase()
        .replace("-", "_");
  }

  private boolean isPropertyConfigured() {
    return configuration.metadata().selector().isPresent()
        && configuration.metadata().keys().isPresent()
        && configuration.metadata().values().isPresent();
  }
}
