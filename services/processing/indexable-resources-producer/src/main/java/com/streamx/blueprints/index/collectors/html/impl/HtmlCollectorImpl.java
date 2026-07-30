package com.streamx.blueprints.index.collectors.html.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.collectors.html.HtmlCollector;
import com.streamx.blueprints.index.configuration.Configuration;
import com.streamx.blueprints.index.configuration.HtmlElementCollectorConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@ApplicationScoped
public class HtmlCollectorImpl extends AbstractHtmlCollector implements HtmlCollector {

  @Inject
  Configuration configuration;

  public Map<String, Object> getElements(Page page, boolean isFacet) {
    return collect(
        configuration.includeHtmlElements(),
        configuration.configurations()
            .values()
            .stream()
            .filter(config -> config.isFacet() == isFacet)
            .toList(),
        this::collectElements,
        page.getContentAsString());
  }

  private Map<String, Object> collectElements(Document document,
      HtmlElementCollectorConfiguration config) {
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
                return config.isFacet()
                    ? getFacetsFromAttributes(config, element).entrySet().stream()
                    : getFieldFromAttributes(config, element).entrySet().stream();
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
    if (isKeyOrValueBlank(key, value)) {
      return Collections.emptyMap();
    }
    return config.hierarchicalFacetDelimiter()
        .map(hierarchicalFacetDelimiter -> createHierarchicalFacets(
            normalizeKey(getKey(key, getKeyDelimiter(config))), value,
            hierarchicalFacetDelimiter))
        .orElse(Map.of(normalizeKey(getKey(key, getKeyDelimiter(config))), value));
  }

  private Map<String, Object> getFieldFromAttributes(HtmlElementCollectorConfiguration config,
      Element element) {
    String key = findFirst(element, config.keys().orElse(Collections.emptyList()));
    String value = findFirst(element, config.values().orElse(Collections.emptyList()));
    if (isKeyOrValueBlank(key, value) || !isKeyValueIndexed(config, key)) {
      return Collections.emptyMap();
    }
    return Map.of(getKey(key, null), value);
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

  private static String getKeyDelimiter(HtmlElementCollectorConfiguration config) {
    return config.keyDelimiter().orElse(null);
  }

  private static boolean isKeyValueIndexed(HtmlElementCollectorConfiguration config, String key) {
    return config.indexedKeys().map(list -> list.contains(key)).orElse(false);
  }
}
