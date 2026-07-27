package com.streamx.blueprints.index.collectors.html.facets.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.collectors.html.AbstractHtmlCollector;
import com.streamx.blueprints.index.collectors.html.facets.FacetsCollector;
import com.streamx.blueprints.index.configuration.Configuration;
import com.streamx.blueprints.index.configuration.FacetsHtmlElementCollectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@ApplicationScoped
public class HtmlFacetsCollector extends AbstractHtmlCollector implements FacetsCollector {

  @Inject
  Configuration configuration;

  public Map<String, Object> getFacets(Page page) {
    return collect(
        configuration.includeFacets(),
        configuration.facetsConfiguration().values(),
        this::collectFacets,
        page.getContentAsString());
  }

  private Map<String, Object> collectFacets(Document document,
      FacetsHtmlElementCollectorConfig config) {
    return config.selector().map(selector ->
        document
            .select(selector)
            .stream()
            .flatMap(element -> {
              if (config.singleAttr()) {
                return config.keys()
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(
                        Collectors.toMap(key -> normalizeKey(getKey(key, getKeyDelimiter(config))),
                            key -> (Object) element.attr(key)))
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
      FacetsHtmlElementCollectorConfig config,
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

  private String getKeyDelimiter(FacetsHtmlElementCollectorConfig config) {
    return config.keyDelimiter().orElse(null);
  }
}
