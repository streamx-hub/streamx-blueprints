package com.streamx.blueprints.index.facets.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.configuration.Configuration;
import com.streamx.blueprints.index.configuration.HtmlElementCollectorConfiguration;
import com.streamx.blueprints.index.facets.FacetsCollector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

@ApplicationScoped
public class HtmlFacetsCollector implements FacetsCollector {

  @Inject
  Configuration configuration;

  public Map<String, Object> getFacets(Page page) {
    if (configuration.includeFacets()) {
      return configuration.configurations()
          .values()
          .stream()
          .flatMap(config -> collectFacets(page, config).entrySet().stream())
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              Map.Entry::getValue
          ));
    }
    return Collections.emptyMap();
  }

  private Map<String, ?> collectFacets(Page page, HtmlElementCollectorConfiguration config) {
    return config.selector().map(selector ->
        Jsoup.parse(page.getContentAsString())
            .select(selector)
            .stream()
            .flatMap(element -> {
              if (config.singleAttr()) {
                return config.keys()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(
                        Collectors.toMap(key -> normalizeKey(getKey(key, getKeyDelimiter(config))),
                            element::attr))
                    .entrySet()
                    .stream();
              } else {
                return getFacetsFromAttributes(config, element).entrySet().stream();
              }
            }).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ))
    ).orElse(Collections.emptyMap());
  }

  private Map<String, Object> getFacetsFromAttributes(
      HtmlElementCollectorConfiguration config,
      Element element) {
    String key = findFirst(element, config.keys().orElse(Collections.emptyList()));
    String value = findFirst(element, config.values().orElse(Collections.emptyList()));
    if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
      return Collections.emptyMap();
    }
    return config.hierarchicalFacetDelimiter()
        .map(hierarchicalFacetDelimiter -> createHierarchicalFacets(
            normalizeKey(getKey(key, getKeyDelimiter(config))), value,
            hierarchicalFacetDelimiter))
        .orElse(Map.of(normalizeKey(getKey(key, getKeyDelimiter(config))), value));
  }

  private static Map<String, Object> createHierarchicalFacets(String key, String value,
      String hierarchicalFacetDelimiter) {
    Map<String, Object> facets = new LinkedHashMap<>();
    String[] parts = value.split(hierarchicalFacetDelimiter);

    facets.put(key + "_path", value);
    for (int i = 0; i < parts.length; i++) {
      facets.put(key + "_level" + i, parts[i]);
    }
    facets.put(key + "_hierarchy", getHierarchy(parts, hierarchicalFacetDelimiter));
    return facets;
  }

  private static List<String> getHierarchy(String[] parts, String hierarchicalFacetDelimiter) {
    List<String> hierarchy = new ArrayList<>();
    StringBuilder currentPath = new StringBuilder();

    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        currentPath.append(hierarchicalFacetDelimiter);
      }
      currentPath.append(parts[i].trim());
      hierarchy.add(currentPath.toString());
    }
    return hierarchy;
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
}
