package com.streamx.blueprints.index.collectors.html.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.collectors.html.HtmlCollector;
import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig;
import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig.Field;
import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig.Processor;
import com.streamx.blueprints.index.processors.ProcessorRegistry;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.NonNull;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@ApplicationScoped
@RegisterForReflection(
    classNames = {
        "com.sun.org.apache.xpath.internal.functions.FuncLocalPart",
        "com.sun.org.apache.xpath.internal.functions.FuncStartsWith"
    }
)
public class HtmlCollectorImpl extends AbstractHtmlCollector implements HtmlCollector {

  private static final XPathFactory XPATH_FACTORY =
      XPathFactory.newInstance();

  @Inject
  SearchFeedExtractorConfig configuration;
  @Inject
  ProcessorRegistry processorRegistry;
  @Inject
  protected Logger log;

  public Map<String, Object> getElements(Page page, boolean isFacet) {
    log.debugf("XPath configuration=%s", configuration.xpath().fields().keySet());
    return collect(
        configuration.xpath().fields()
            .values()
            .stream()
            .filter(config -> config.facet() == isFacet)
            .toList(),
        this::collectElements,
        page.getContentAsString());
  }

  private Map<String, Object> collectElements(Document document, Field config) {
    Map<String, Object> elements = new HashMap<>();
    String elementSelector = config.elementSelector().orElse(StringUtils.EMPTY);
    org.w3c.dom.Document xmlDoc = new W3CDom().fromJsoup(document);
    log.debugf("Elements selector=%s", elementSelector);
    log.debugf("XML document=%s", xmlDoc);

    try {
      NodeList nodeList = (NodeList) XPATH_FACTORY.newXPath()
          .evaluate(elementSelector, xmlDoc, XPathConstants.NODESET);

      log.debugf("Node List size=%s", nodeList.getLength());
      for (int i = 0; i < nodeList.getLength(); i++) {
        Node node = nodeList.item(i);

        Map<String, Object> current = config.facet()
            ? getFacetsFromAttributes(config, node)
            : getFieldsFromAttributes(config, node);

        elements.putAll(current);
      }
    } catch (XPathExpressionException e) {
      log.warnf(e, "Error during evaluating XPath expression");
    }

    log.debugf("Collected elements=%s", elements);
    return elements;
  }

  private Map<String, Object> getFacetsFromAttributes(Field config, Node element) {
    String key = getText(config.keySelector(), config.key(), element);
    String value = getText(config.valueSelector(), config.value(), element);
    log.debugf("Facet key=%s, value=%s", key, value);
    if (StringUtils.isAnyBlank(key, value)) {
      return Collections.emptyMap();
    }

    return createHierarchicalFacets(applyProcessors(key, config.keyProcessors()).getFirst(), value,
        config.valueProcessors());
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

  private Map<String, Object> getFieldsFromAttributes(Field config, Node element) {
    String key = getText(config.keySelector(), config.key(), element);
    String value = getText(config.valueSelector(), config.key(), element);
    log.debugf("Field key=%s, value=%s", key, value);
    if (StringUtils.isAnyBlank(key, value)) {
      return Collections.emptyMap();
    }
    return Map.of(applyProcessors(key, config.keyProcessors()).getFirst(),
        applyProcessors(value, config.valueProcessors()).getFirst());
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
      result = processorRegistry.get(processor.name())
          .process(result, processor.config().orElse(null));
    }
    return result;
  }
}
