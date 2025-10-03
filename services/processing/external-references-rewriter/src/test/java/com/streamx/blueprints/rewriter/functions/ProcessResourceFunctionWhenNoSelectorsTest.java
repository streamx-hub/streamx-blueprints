package com.streamx.blueprints.rewriter.functions;

import static org.mockito.Mockito.doReturn;

import com.streamx.blueprints.rewriter.configuration.Configuration;
import com.streamx.blueprints.rewriter.testutils.ForceRecreatingBeans;
import io.cloudevents.CloudEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
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
    publishPage(pagePath, pageContent);
    publishWebResource(sitemapPath, sitemapContent);
    publishWebResource(jsonIndexPath, jsonIndexContent);

    // then
    List<CloudEvent> pageEvents = waitForEventsInSink(PAGE, 1);
    List<CloudEvent> webResourceEvents = waitForEventsInSink(WEB_RESOURCE, 2);

    assertPublishedPage(pageEvents.get(0), pagePath, pageContent);
    assertPublishedWebResource(webResourceEvents.get(0), sitemapPath, sitemapContent);
    assertPublishedWebResource(webResourceEvents.get(1), jsonIndexPath, jsonIndexContent);
  }
}
