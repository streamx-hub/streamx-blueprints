package com.streamx.blueprints.index.facets.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.configuration.Configuration;
import com.streamx.blueprints.index.configuration.HtmlElementCollectorConfiguration;
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
public class HtmlFacetsCollector implements FacetsCollector {

  @Inject
  Configuration configuration;

  public Map<String, String> getFacets(Page page) {
    if (!configuration.includeFacets()) {
      return Collections.emptyMap();
    }
    Map<String, String> facets = new HashMap<>();
    for (HtmlElementCollectorConfiguration config : configuration.configurations().values()) {
      if (isPropertyConfigured(config)) {
        collectFacets(page, facets, config);
      }
    }
    return facets;
  }

  private void collectFacets(Page page, Map<String, String> facets,
      HtmlElementCollectorConfiguration config) {
    if (config.values().isEmpty() && !config.singleAttr()) {
      return;
    }

    Document doc = Jsoup.parse(page.getContentAsString());
    Elements elements = doc.select(config.selector().get());

    for (Element element : elements) {
      if (config.singleAttr()) {
        collectSingleAttr(facets, config, element);
      } else {
        String key = findFirst(element, config.keys().get());
        String value = findFirst(element, config.values().get());
        facets.put(normalizeKey(getKey(key, getKeyDelimiter(config))), value);
      }
    }
  }

  private void collectSingleAttr(Map<String, String> facets,
      HtmlElementCollectorConfiguration config,
      Element element) {
    config.keys().get().forEach(key -> {
      String value = element.attr(key);
      if (StringUtils.isNotBlank(value)) {
        facets.put(normalizeKey(getKey(key, getKeyDelimiter(config))), value);
      }
    });
  }

  private String getKeyDelimiter(HtmlElementCollectorConfiguration config) {
    return config.keyDelimiter().orElse(null);
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

  private boolean isPropertyConfigured(HtmlElementCollectorConfiguration config) {
    return config.selector().isPresent() && config.keys().isPresent();
  }
}
