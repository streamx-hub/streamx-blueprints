package com.streamx.blueprints.rewriter.finders;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jboss.logging.Logger;

public abstract class BaseValuesFinder {

  private final Logger log;

  public BaseValuesFinder() {
    log = Logger.getLogger(getClass());
  }

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
        log.warnf("Exception: %s while processing input: %s", ex.getMessage(), inputContent);
      }
    }
    return Set.of();
  }
}
