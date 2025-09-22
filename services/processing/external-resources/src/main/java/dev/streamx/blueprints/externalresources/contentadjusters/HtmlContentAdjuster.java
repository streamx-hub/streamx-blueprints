package dev.streamx.blueprints.externalresources.contentadjusters;

import java.util.regex.Pattern;
import org.apache.commons.lang.StringEscapeUtils;

public class HtmlContentAdjuster extends BaseResourceContentAdjuster {

  @Override
  protected String fixedInputContent(String inputContent) {
    // external resources are read from cleaned up html, so we must first prepare the input content:
    return StringEscapeUtils.unescapeHtml(inputContent);
  }

  @Override
  protected Pattern inputUrlPattern(String externalResourcePath) {
    return Pattern.compile(
        "(['\"])" // opening quote of url attribute in html content
        + "(" + Pattern.quote(externalResourcePath) + ")" // the url
        + "((?:#\\S*)?)" // optional anchor
        + "(['\"])" // closing quote
    );
  }

  @Override
  protected int sourceUrlGroupNumber() {
    return 2;
  }
}