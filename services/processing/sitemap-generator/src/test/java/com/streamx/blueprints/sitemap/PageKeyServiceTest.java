package com.streamx.blueprints.sitemap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageKeyServiceTest {

  private static final String KEY_MATCHING_HTML_FILE_PATTERN = "/*.html";

  @Test
  void shouldSupportAnyKeyWhenNoPatternDefined() {
    PageKeyService service = new PageKeyService();

    boolean isSupportedForAnyKey = service.isSupportedKey("anyKey");

    assertThat(isSupportedForAnyKey).isTrue();
  }

  @Test
  void shouldSupportKeyMatchingPatternWhenPatternIsDefined() {
    PageKeyService service = new PageKeyService(KEY_MATCHING_HTML_FILE_PATTERN);

    boolean isSupported = service.isSupportedKey("/anyKey.html");

    assertThat(isSupported).isTrue();
  }

  @Test
  void shouldNotSupportKeyNotMatchingPatternWhenPatternIsDefined() {
    PageKeyService service = new PageKeyService(KEY_MATCHING_HTML_FILE_PATTERN);

    boolean isSupported = service.isSupportedKey("/anyKey.js");

    assertThat(isSupported).isFalse();
  }

  @Test
  void shouldSupportKeyMatchingOneOfDefinedPattern() {
    PageKeyService service = new PageKeyService(KEY_MATCHING_HTML_FILE_PATTERN, "/*.js");

    boolean isSupported = service.isSupportedKey("/anyKey.js");

    assertThat(isSupported).isTrue();
  }
}
