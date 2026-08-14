package com.streamx.blueprints.index.collectors.html.impl;

import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig;
import com.streamx.blueprints.index.configuration.SearchFeedExtractorConfig.Field;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@ApplicationScoped
@RegisterForReflection(
    classNames = {
        "com.sun.org.apache.xpath.internal.functions.FuncLocalPart",
        "com.sun.org.apache.xpath.internal.functions.FuncStartsWith"
    }
)
public class HtmlCollectorImpl extends AbstractHtmlCollector {

  private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

  @Inject
  Logger log;
  @Inject
  SearchFeedExtractorConfig configuration;
  @Inject
  FieldsCollectorImpl fieldsCollector;

  public Map<String, Object> getFacets(Page page) {
    return getElements(page, Field::facet);
  }

  public Map<String, Object> getFields(Page page) {
    return getElements(page, Predicate.not(Field::facet));
  }

  private Map<String, Object> getElements(Page page, Predicate<Field> filter) {
    log.debugf("XPath configuration=%s", configuration.xpath().fields().keySet());
    return collect(
        configuration.xpath().fields()
            .values()
            .stream()
            .filter(filter)
            .toList(),
        this::collectElements,
        page.getContentAsString());
  }

  public boolean noIndex(Page page) {
    List<Field> noIndexConfig = configuration.xpath().fields().values()
        .stream()
        .filter(Field::noIndex)
        .toList();

    if (noIndexConfig.isEmpty()) {
      return false;
    }

    Map<String, Object> noIndex = collect(
        noIndexConfig,
        this::collectElements,
        page.getContentAsString()
    );
    return !noIndex.isEmpty();
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
            ? fieldsCollector.getFacetsFromAttributes(config, node)
            : fieldsCollector.getFieldsFromAttributes(config, node);

        elements.putAll(current);
      }
    } catch (XPathExpressionException e) {
      log.warnf(e, "Error during evaluating XPath expression");
    }

    log.debugf("Collected elements=%s", elements);
    return elements;
  }
}
