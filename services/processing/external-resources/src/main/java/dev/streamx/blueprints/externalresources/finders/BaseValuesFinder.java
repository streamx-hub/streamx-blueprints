package dev.streamx.blueprints.externalresources.finders;

import io.quarkus.logging.Log;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class BaseValuesFinder {

  protected abstract Set<String> doFindMatchingValues(String inputContent,
      List<String> searchExpressions) throws Exception;

  public final Set<String> findMatchingValues(String inputContent, List<String> searchExpressions) {
    Set<String> distinctSearchExpressions = Optional.ofNullable(searchExpressions)
        .map(LinkedHashSet::new)
        .orElseGet(LinkedHashSet::new);

    if (!distinctSearchExpressions.isEmpty()) {
      try {
        return doFindMatchingValues(inputContent, searchExpressions);
      } catch (Exception ex) {
        // Content is an external resource, we should isolate parsing exception
        Log.warnf("Exception while processing input: %s", inputContent);
      }
    }
    return Set.of();
  }
}
