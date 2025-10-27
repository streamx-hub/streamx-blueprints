package com.streamx.blueprints.rewriter.finders;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.language.xpath.XPathBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XmlValuesFinder extends BaseValuesFinder {

  private static final CamelContext camelContext = new DefaultCamelContext();
  private static final Pattern XPATH_FUNCTION_CALL_REGEX = Pattern
      .compile("^([a-zA-Z_][a-zA-Z0-9_-]*)\\(.*");

  @Override
  public Set<String> doFindMatchingValues(String inputContent, List<String> xpathExpressions)
      throws Exception {
    String fixedInputContent = fixInputContent(inputContent);
    Document document = parseToDocument(fixedInputContent);

    Set<String> foundValues = new LinkedHashSet<>();
    for (String xpathExpression : xpathExpressions) {
      try (XPathBuilder xpath = new XPathBuilder(xpathExpression)) {
        List<String> matchingValues = findMatchingValues(xpath, document);
        matchingValues.forEach(value -> addValue(value, foundValues, xpathExpression));
      }
    }
    return foundValues;
  }

  private List<String> findMatchingValues(XPathBuilder xpath, Document document) {
    if (isFunctionCall(xpath.getExpressionText())) {
      return List.of(xpath.evaluate(camelContext, document));
    }

    Object result = xpath.evaluate(camelContext, document, Object.class);

    if (result instanceof String value) {
      return List.of(value);
    }

    if (result instanceof NodeList nodes) {
      List<String> values = new ArrayList<>();
      for (int i = 0; i < nodes.getLength(); i++) {
        values.add(nodes.item(i).getTextContent().trim());
      }
      return values;
    }

    throw new UnsupportedOperationException(result.getClass() + " is not handled yet");
  }

  private boolean isFunctionCall(String xpathExpression) {
    return XPATH_FUNCTION_CALL_REGEX.matcher(xpathExpression).matches();
  }

  private Document parseToDocument(String input) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();

    ByteArrayInputStream bytesStream = new ByteArrayInputStream(input.getBytes(UTF_8));
    return builder.parse(bytesStream);
  }

  /**
   * Implement cleaning up the input.
   *
   * @return Valid well-formed XML
   */
  protected String fixInputContent(String inputContent) {
    // override if needed
    return inputContent;
  }

  protected void addValue(String value, Set<String> foundValues, String sourceXpathExpression) {
    addValue(value, foundValues);
  }

  protected static void addValue(String value, Set<String> foundValues) {
    String trimmedValue = value.trim();
    if (!trimmedValue.isEmpty()) {
      foundValues.add(trimmedValue);
    }
  }
}
