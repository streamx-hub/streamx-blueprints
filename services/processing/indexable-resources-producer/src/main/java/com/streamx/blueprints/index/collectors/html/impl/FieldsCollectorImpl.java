package com.streamx.blueprints.index.collectors.html.impl;

import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig.Field;
import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig.Processor;
import com.streamx.blueprints.index.processors.string.StringProcessor;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jspecify.annotations.NonNull;
import org.w3c.dom.Node;

@ApplicationScoped
public class FieldsCollectorImpl {

  private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

  @Inject
  Logger log;
  @Inject
  @All
  List<StringProcessor> processors;

  public Map<String, Object> getFacetsFromAttributes(Field config, Node element) {
    String key = getText(config.keySelector(), config.key(), element);
    String value = getText(config.valueSelector(), config.value(), element);
    log.debugf("Facet key=%s, value=%s", key, value);
    if (StringUtils.isAnyBlank(key, value)) {
      return Collections.emptyMap();
    }

    return createHierarchicalFacets(applyProcessors(key, config.keyProcessors()).getFirst(), value,
        config.valueProcessors());
  }

  public Map<String, Object> getFieldsFromAttributes(Field config, Node element) {
    String key = getText(config.keySelector(), config.key(), element);
    String value = getText(config.valueSelector(), config.value(), element);
    log.debugf("Field key=%s, value=%s", key, value);
    if (StringUtils.isAnyBlank(key, value)) {
      return Collections.emptyMap();
    }
    return Map.of(applyProcessors(key, config.keyProcessors()).getFirst(),
        applyProcessors(value, config.valueProcessors()).getFirst());
  }

  private @NonNull String getText(Optional<String> config, Optional<String> defaultValue,
      Node nodeValue) {
    return config.map(selector -> {
      try {
        return XPATH_FACTORY.newXPath().evaluate(selector, nodeValue);
      } catch (XPathExpressionException e) {
        log.warnf(e, "Error during evaluating XPath expression");
        return StringUtils.EMPTY;
      }
    }).orElse(defaultValue.orElse(StringUtils.EMPTY));
  }

  private Map<String, Object> createHierarchicalFacets(String key, String value,
      List<Processor> valueProcessors) {
    Map<String, Object> facets = new LinkedHashMap<>();
    List<String> parts = applyProcessors(value, valueProcessors);

    facets.put(key + "_path", value);
    for (int i = 0; i < parts.size(); i++) {
      facets.put(key + "_level" + i, parts.get(i));
    }
    facets.put(key + "_hierarchy", getHierarchy(parts, valueProcessors.stream()
        .filter(processor -> "split".equals(processor.name()))
        .flatMap(processor -> processor.config().stream())
        .findFirst()
        .orElse(StringUtils.EMPTY)));
    return facets;
  }

  private static List<String> getHierarchy(List<String> parts, String hierarchicalFacetDelimiter) {
    List<String> hierarchy = new ArrayList<>();
    StringBuilder currentPath = new StringBuilder();

    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        currentPath.append(hierarchicalFacetDelimiter);
      }
      currentPath.append(parts.get(i).trim());
      hierarchy.add(currentPath.toString());
    }
    return hierarchy;
  }

  private List<String> applyProcessors(String string, List<Processor> processors) {
    List<String> result = List.of(string);
    for (Processor processor : processors) {
      StringProcessor stringProcessor = this.processors.stream()
          .filter(p -> p.getName().equals(processor.name()))
          .findFirst()
          .orElse(null);
      if (stringProcessor != null) {
        result = stringProcessor.process(result, processor.config().orElse(null));
      }
    }
    return result;
  }
}
