package dev.streamx.blueprints.externalresources.services;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class UrlComputationServiceTest {

  @Inject
  UrlComputationService service;

  @ParameterizedTest
  @CsvSource(textBlock = """
      http://host1/dir/,       http://host2/page.html, http://host2/page.html
      http://host1/dir/,       page.html,              http://host1/dir/page.html
      http://host1/dir/,       /page.html,             http://host1/page.html
      http://host1/dir/,       ./page.html,            http://host1/dir/page.html
      http://host1/dir1/dir2/, ../page.html,           http://host1/dir1/page.html
      http://host1/dir/,       page with illegal ^ chars.html?a=b,              http://host1/dir/page%20with%20illegal%20%5E%20chars.html?a=b
      http://host1/dir/,       http://host2/page with illegal ^ chars.html?a=b, http://host2/page%20with%20illegal%20%5E%20chars.html?a=b
      """)
  void testComputeAbsoluteUrl(String parentAbsoluteUrl, String relativeOrAbsoluteUrl,
      String expectedResult) {
    // when
    String actualResult = service.computeAbsoluteUrl(parentAbsoluteUrl, relativeOrAbsoluteUrl);

    // then
    assertThat(actualResult).isEqualTo(expectedResult);
  }

  @ParameterizedTest
  @CsvSource(delimiterString = "->", textBlock = """
      http://www.server.com                                  -> /http_www.server.com
      http://www.server.com/                                 -> /http_www.server.com/
      http://www.server.com/index                            -> /http_www.server.com/index
      http://www.server.com/page.html                        -> /http_www.server.com/page.html
      http://www.server.com/page-🙂-with-ą-ę.html            -> /http_www.server.com/page-_-with-_-_.html
      http://www.server.com/dir                              -> /http_www.server.com/dir
      http://www.server.com/dir/page.html                    -> /http_www.server.com/dir/page.html
      http://www.server.com/dir1/page.html/dir3              -> /http_www.server.com/dir1/page.html/dir3
      http://www.server.com/dir1/dir2                        -> /http_www.server.com/dir1/dir2
      http://www.server.com/dir1/dir2/page.html              -> /http_www.server.com/dir1/dir2/page.html
      http://www.server.com/image.jpg?key=value&key2=value2  -> /http_www.server.com/image.jpg_key_value_key2_value2.jpg
      https://www.server.com/resource?key=value              -> /https_www.server.com/resource_key_value
      invalid^url                                            -> /invalid_url
      /relative/url/1                                        -> /relative/url/1
      relative/url/2                                         -> /relative/url/2
      /                                                      -> /
      default_product:1                                      -> /default_product_1
      category:2                                             -> /category_2
      namespace:3:hash                                       -> /namespace_3_hash
      http://www.server.com/namespace:id:hash                -> /http_www.server.com/namespace_id_hash
      mailto:user@example.com                                -> /mailto_user_example.com
      """)
  void testAsStreamxKey(String url, String expectedResult) {
    // when
    String actualResult = service.asStreamxKey(url);

    // then
    assertThat(actualResult).isEqualTo(expectedResult);
  }

}