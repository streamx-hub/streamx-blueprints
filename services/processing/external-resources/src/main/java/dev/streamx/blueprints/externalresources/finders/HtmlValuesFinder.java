package dev.streamx.blueprints.externalresources.finders;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Entities.EscapeMode;

public class HtmlValuesFinder extends XmlValuesFinder {

  private static final Pattern srcSetItemsSeparator = Pattern.compile("[,\\s]");
  private static final Pattern srcSetConditionDescriptorPattern = Pattern
      .compile("^\\d+\\.?\\d+[wx]$");

  @Override
  protected String fixInputContent(String inputContent) {
    String withDocTypeRemoved = inputContent.replaceFirst("(?i)<!DOCTYPE[^>]*>", "");
    Document document = Jsoup.parse(withDocTypeRemoved);
    document.outputSettings()
        .syntax(OutputSettings.Syntax.xml)
        .escapeMode(EscapeMode.xhtml);
    return document.outerHtml();
  }

  @Override
  protected void addValue(String value, Set<String> foundValues, String sourceXpathExpression) {
    if (sourceXpathExpression.endsWith("@srcset")) {
      Arrays.stream(srcSetItemsSeparator.split(value))
          .filter(item -> !srcSetConditionDescriptorPattern.matcher(item).matches())
          .forEach(item -> addValue(item, foundValues));
    } else {
      super.addValue(value, foundValues, sourceXpathExpression);
    }
  }
}
