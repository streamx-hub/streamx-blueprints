package dev.streamx.blueprints.externalresources.functions;

import static org.mockito.Mockito.doReturn;

import dev.streamx.blueprints.data.Page;
import dev.streamx.blueprints.data.WebResource;
import dev.streamx.blueprints.externalresources.configuration.Configuration;
import dev.streamx.blueprints.externalresources.testutils.ForceRecreatingBeans;
import io.quarkus.test.InjectMock;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ForceRecreatingBeans.class)
public class ProcessResourceFunctionWhenNoSelectorsTest extends BaseProcessFunctionTest {

  @Inject
  SmallRyeConfig smallRyeConfig;

  @ApplicationScoped
  @Mock
  Configuration configuration() {
    return smallRyeConfig.getConfigMapping(Configuration.class);
  }

  @InjectMock
  Configuration configuration;

  @BeforeEach
  void initConfig() {
    doReturn(Optional.empty()).when(configuration).htmlExternalResourceXpathSelectors();
    doReturn(Optional.empty()).when(configuration).xmlExternalResourceXpathSelectors();
    doReturn(Optional.empty()).when(configuration).jsonExternalResourceJsonpathSelectors();
  }

  @Test
  void shouldRelayResources() {
    // given
    final String pagePath = "/page1.html";
    final String pageContent = """
        <a href='page2.html'>Page 2</a>
        <a href='page3.html'>Page 3/a>
        """;

    final String sitemapPath = "/sitemap.xml";
    final String sitemapContent = """
        <?xml version="1.0" encoding="utf-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url>
            <loc>http://www.goggle.com/page1.html</loc>
          </url>
          <url>
            <loc>http://www.goggle.com/page2.html</loc>
          </url>
        </urlset>
        """;

    final String jsonIndexPath = "/data/en-index.json";
    final String jsonIndexContent = """
        {
           "data": [
             {
               "path": "/en/article-1",
               "title": "Article 1",
               "image": "/article-1-image.png?width=1200&format=pjpg&optimize=medium"
             },
             {
               "path": "/en/article-2",
               "title": "Article 2",
               "image": "/article-2-image.png?width=1200&format=pjpg&optimize=medium"
             }
          ]
        }
        """;

    // when
    publish(pagesChannel, new Page(pageContent), pagePath, "any");
    publish(webResourcesChannel, new WebResource(sitemapContent), sitemapPath, "any");
    publish(webResourcesChannel, new WebResource(jsonIndexContent), jsonIndexPath, "any");

    // then
    waitForMessagesInSink(pagesSink, 1);
    waitForMessagesInSink(webResourcesSink, 2);

    assertPublishedPage(0, pagePath, pageContent);
    assertPublishedWebResource(0, sitemapPath, sitemapContent);
    assertPublishedWebResource(1, jsonIndexPath, jsonIndexContent);
  }
}
