package com.streamx.blueprints.externalresources.services;

import com.streamx.blueprints.externalresources.data.ExternalResource;
import com.streamx.blueprints.externalresources.data.ParentResource;
import com.streamx.blueprints.externalresources.finders.BaseValuesFinder;
import com.streamx.blueprints.externalresources.functions.settings.contentprocessing.BaseContentProcessingSettings;
import io.quarkus.logging.Log;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class ExternalResourcesCollector {

  private final UrlComputationService urlComputationService;
  private final BaseValuesFinder valuesFinder;
  private final List<String> resourceSelectors;
  private final Optional<Pattern> resourceUrlExclusionsPattern;

  public ExternalResourcesCollector(BaseContentProcessingSettings settings,
      UrlComputationService urlComputationService) {
    this.urlComputationService = urlComputationService;
    this.valuesFinder = settings.getValuesFinder();
    this.resourceSelectors = settings.getResourceSelectors().orElseGet(Collections::emptyList);
    this.resourceUrlExclusionsPattern = settings.getResourceUrlExclusionsPattern();
  }

  public boolean hasResourceSelectors() {
    return !resourceSelectors.isEmpty();
  }

  public Set<ExternalResource> collectExternalResources(ParentResource<?> parentResource) {
    Set<String> resourcePaths = valuesFinder
        .findMatchingValues(parentResource.getContent(), resourceSelectors)
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

  private void addResource(String path, ParentResource<?> parent, Set<ExternalResource> resources) {
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
  private String getValidatedAbsoluteUrl(String path, ParentResource<?> parentResource) {
    String absoluteUrl;
    try {
      absoluteUrl = urlComputationService.computeAbsoluteUrl(parentResource.getAbsoluteUrl(), path);
    } catch (RuntimeException ex) {
      Log.errorf(ex, "Error computing absolute url for %s found in parent resource %s",
          path, parentResource.getStreamxKey());
      return null;
    }

    if (!urlComputationService.isAbsoluteHttpUrl(absoluteUrl)) {
      Log.warnf("Skipping non http(s) url %s found in parent resource %s",
          absoluteUrl, parentResource.getStreamxKey());
      return null;
    }

    if (isUrlExcluded(absoluteUrl)) {
      Log.tracef("Skipping external resource %s because its URL is excluded", absoluteUrl);
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
  private String getValidatedStreamxKey(String absoluteUrl, ParentResource<?> parentResource) {
    String streamxKey;
    try {
      streamxKey = urlComputationService.asStreamxKeyRelativeToConfiguredBaseUrl(absoluteUrl);
    } catch (Exception ex) {
      Log.tracef("Skipping external resource %s because it has an invalid URL", absoluteUrl);
      return null;
    }

    if (streamxKey.equals(parentResource.getStreamxKey())) {
      Log.tracef("Skipping external resource %s because it's same as parent resource",
          absoluteUrl);
      return null;
    }
    return streamxKey;
  }
}