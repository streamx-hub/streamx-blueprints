package com.streamx.blueprints.rewriter.services;

import com.streamx.blueprints.rewriter.data.ExternalResource;
import com.streamx.blueprints.rewriter.data.ResourceData;
import com.streamx.blueprints.rewriter.finders.BaseValuesFinder;
import com.streamx.blueprints.rewriter.functions.settings.contentprocessing.BaseContentProcessingSettings;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

public class ExternalResourcesCollector {

  private final Logger log;

  private final UrlComputationService urlComputationService;
  private final BaseValuesFinder valuesFinder;
  private final List<String> resourceSelectors;
  private final Optional<Pattern> resourceUrlExclusionsPattern;

  public ExternalResourcesCollector(BaseContentProcessingSettings settings,
      UrlComputationService urlComputationService) {
    this.log = Logger.getLogger(getClass());
    this.urlComputationService = urlComputationService;
    this.valuesFinder = settings.getValuesFinder();
    this.resourceSelectors = settings.getResourceSelectors().orElseGet(Collections::emptyList);
    this.resourceUrlExclusionsPattern = settings.getResourceUrlExclusionsPattern();
  }

  public boolean hasResourceSelectors() {
    return !resourceSelectors.isEmpty();
  }

  public Set<ExternalResource> collectExternalResources(ResourceData parentResource) {
    Set<String> resourcePaths = valuesFinder
        .findMatchingValues(parentResource.content(), resourceSelectors)
        .stream()
        .filter(path -> !path.startsWith("#")) // link to own section - no action needed
        .map(path -> StringUtils.substringBefore(path, "#")) // use base url if anchor in url
        .collect(Collectors.toCollection(LinkedHashSet::new));

    Set<ExternalResource> resources = new LinkedHashSet<>();
    for (String resourcePath : resourcePaths) {
      addResource(resourcePath, parentResource, resources);
    }
    return resources;
  }

  private void addResource(String path, ResourceData parent, Set<ExternalResource> resources) {
    String absoluteUrl = getValidatedAbsoluteUrl(path, parent);
    if (absoluteUrl == null) {
      return;
    }

    String streamxKey = getValidatedStreamxKey(absoluteUrl, parent);
    if (streamxKey == null) {
      return;
    }

    for (ExternalResource resource : resources) {
      if (resource.getAbsoluteUrl().equals(absoluteUrl)) {
        resource.addPath(path);
        return;
      }
    }

    resources.add(new ExternalResource(path, absoluteUrl, streamxKey));
  }

  @Nullable
  private String getValidatedAbsoluteUrl(String path, ResourceData parentResource) {
    String absoluteUrl;
    try {
      absoluteUrl = urlComputationService.computeAbsoluteUrl(parentResource.absoluteUrl(), path);
    } catch (RuntimeException ex) {
      log.errorf(ex, "Error computing absolute url for %s found in parent resource %s",
          path, parentResource.streamxKey());
      return null;
    }

    if (!urlComputationService.isAbsoluteHttpUrl(absoluteUrl)) {
      log.warnf("Skipping non http(s) url %s found in parent resource %s",
          absoluteUrl, parentResource.streamxKey());
      return null;
    }

    if (isUrlExcluded(absoluteUrl)) {
      log.tracef("Skipping external resource %s because its URL is excluded", absoluteUrl);
      return null;
    }
    return absoluteUrl;
  }

  private boolean isUrlExcluded(String resourceUrl) {
    return resourceUrlExclusionsPattern
        .map(pattern -> pattern.matcher(resourceUrl).matches())
        .orElse(false);
  }

  @Nullable
  private String getValidatedStreamxKey(String absoluteUrl, ResourceData parentResource) {
    String streamxKey;
    try {
      streamxKey = urlComputationService.asStreamxKeyRelativeToConfiguredBaseUrl(absoluteUrl);
    } catch (Exception ex) {
      log.tracef("Skipping external resource %s because it has an invalid URL", absoluteUrl);
      return null;
    }

    if (streamxKey.equals(parentResource.streamxKey())) {
      log.tracef("Skipping external resource %s because it's same as parent resource",
          absoluteUrl);
      return null;
    }
    return streamxKey;
  }
}