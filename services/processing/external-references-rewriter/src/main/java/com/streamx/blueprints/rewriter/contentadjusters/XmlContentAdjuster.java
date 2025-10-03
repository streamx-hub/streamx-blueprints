package com.streamx.blueprints.rewriter.contentadjusters;

import java.util.regex.Pattern;

public class XmlContentAdjuster extends BaseResourceContentAdjuster {

  @Override
  protected Pattern inputUrlPattern(String externalResourcePath) {
    return Pattern.compile(
        "(>)"
        + "(" + Pattern.quote(externalResourcePath) + ")"
        + "(<)"
    );
  }

  @Override
  protected int sourceUrlGroupNumber() {
    return 2;
  }
}