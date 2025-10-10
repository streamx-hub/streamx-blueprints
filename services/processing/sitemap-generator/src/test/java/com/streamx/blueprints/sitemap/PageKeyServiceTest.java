package com.streamx.blueprints.sitemap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PageKeyServiceTest {

  private static final String KEY_MATCHING_HTML_FILE_PATTERN = "/*.html";

  @Test
  void shouldSupportAnyKeyWhenNoPatternDefined() {
    PageKeyService service = new PageKeyService();

    boolean isSupportedForAnyKey = service.isSupportedKey("anyKey");

    assertTrue(isSupportedForAnyKey);
  }

  @Test
  void shouldSupportKeyMatchingPatternWhenPatternIsDefined() {
    PageKeyService service = new PageKeyService(KEY_MATCHING_HTML_FILE_PATTERN);

    boolean isSupported = service.isSupportedKey("/anyKey.html");

    assertTrue(isSupported);
  }

  @Test
  void shouldNotSupportKeyNotMatchingPatternWhenPatternIsDefined() {
    PageKeyService service = new PageKeyService(KEY_MATCHING_HTML_FILE_PATTERN);

    boolean isSupported = service.isSupportedKey("/anyKey.js");

    assertFalse(isSupported);
  }

  @Test
  void shouldSupportKeyMatchingOneOfDefinedPattern() {
    PageKeyService service = new PageKeyService(KEY_MATCHING_HTML_FILE_PATTERN, "/*.js");

    boolean isSupported = service.isSupportedKey("/anyKey.js");

    assertTrue(isSupported);
  }
}
