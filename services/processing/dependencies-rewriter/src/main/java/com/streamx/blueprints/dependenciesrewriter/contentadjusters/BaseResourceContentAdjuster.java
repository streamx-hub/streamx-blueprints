package com.streamx.blueprints.dependenciesrewriter.contentadjusters;

import com.streamx.blueprints.dependenciesrewriter.data.ExternalResource;
import io.quarkus.logging.Log;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseResourceContentAdjuster {

  protected abstract Pattern inputUrlPattern(String externalResourcePath);

  /**
   * @return number of the group with source url in {@link #inputUrlPattern}
   */
  protected abstract int sourceUrlGroupNumber();

  protected String fixedInputContent(String inputContent) {
    return inputContent;
  }

  public final String adjustLinks(String inputContent, Set<ExternalResource> externalResources) {
    inputContent = fixedInputContent(inputContent);
    for (ExternalResource resource : externalResources) {
      for (String resourcePath : resource.getPaths()) {
        Pattern inputUrlPattern = inputUrlPattern(resourcePath);
        Matcher matcher = inputUrlPattern.matcher(inputContent);
        if (matcher.find()) {
          String replacementUrl = resource.getStreamxKey();
          inputContent = replaceUrl(matcher, replacementUrl);
        } else {
          Log.warnf("Didn't replace %s", resourcePath);
        }
      }
    }
    return inputContent;
  }

  private String replaceUrl(Matcher matcher, String replacementUrl) {
    StringBuilder result = new StringBuilder();

    do {
      StringBuilder replacement = new StringBuilder();
      for (int groupNumber = 1; groupNumber <= matcher.groupCount(); groupNumber++) {
        if (groupNumber == sourceUrlGroupNumber()) {
          replacement.append(replacementUrl);
        } else {
          replacement.append(matcher.group(groupNumber));
        }
      }

      Log.tracef("Replacing %s to %s", matcher.group(0), replacement);
      matcher.appendReplacement(result, replacement.toString());
    } while (matcher.find());

    matcher.appendTail(result);

    return result.toString();
  }
}