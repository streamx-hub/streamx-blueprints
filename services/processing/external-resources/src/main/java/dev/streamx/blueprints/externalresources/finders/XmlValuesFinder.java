package dev.streamx.blueprints.externalresources.finders;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathEvaluationResult.XPathResultType;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class XmlValuesFinder extends BaseValuesFinder {

  private static final XPathFactory xpathFactory = XPathFactory.newInstance();

  @Override
  public Set<String> doFindMatchingValues(String inputContent, List<String> xpathExpressions)
      throws Exception {
    String fixedInputContent = fixInputContent(inputContent);
    Document document = parseToDocument(fixedInputContent);
    XPath xpath = xpathFactory.newXPath();

    Set<String> foundValues = new LinkedHashSet<>();
    for (String xpathExpression : xpathExpressions) {
      findMatchingValues(document, xpathExpression, xpath, foundValues);
    }
    return foundValues;
  }

  private void findMatchingValues(Document document, String xpathExpression,
      XPath xpath, Set<String> foundValues) throws XPathExpressionException {
    XPathExpression compiledExpression = xpath.compile(xpathExpression);

    XPathEvaluationResult<?> evaluationResult = compiledExpression.evaluateExpression(document);
    XPathResultType type = evaluationResult.type();

    if (type == XPathResultType.STRING) {
      String value = (String) evaluationResult.value();
      addValue(value, foundValues, xpathExpression);
    } else if (type == XPathResultType.NODESET) {
      XPathNodes nodes = (XPathNodes) evaluationResult.value();
      for (Node node : nodes) {
        String value = node.getTextContent().trim();
        addValue(value, foundValues, xpathExpression);
      }
    } else {
      throw new UnsupportedOperationException(type + " is not handled yet");
    }
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
