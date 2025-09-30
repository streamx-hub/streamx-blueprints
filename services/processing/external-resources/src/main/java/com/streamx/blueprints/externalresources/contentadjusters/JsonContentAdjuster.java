package com.streamx.blueprints.externalresources.contentadjusters;

import java.util.regex.Pattern;

public class JsonContentAdjuster extends BaseResourceContentAdjuster {

  @Override
  protected String fixedInputContent(String inputContent) {
    // external resources are read from cleaned up json, so we must first prepare the input content:
    return inputContent.replace("\\/", "/");
  }

  @Override
  protected Pattern inputUrlPattern(String externalResourcePath) {
    return Pattern.compile(
        "(\")"
        + "(" + Pattern.quote(externalResourcePath) + ")"
        + "((?:#\\S*)?)" // optional anchor
        + "(\")"
    );
  }

  @Override
  protected int sourceUrlGroupNumber() {
    return 2;
  }
}