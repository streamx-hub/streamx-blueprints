package com.streamx.blueprints.composition;

import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.eventWithData;
import static com.streamx.blueprints.cloudevents.utils.CloudEventUtils.eventWithoutData;
import static com.streamx.blueprints.composition.Channels.INCOMING_COMPOSITIONS;
import static com.streamx.blueprints.composition.Channels.INCOMING_COMPOSITIONS_STATE;
import static com.streamx.blueprints.composition.Channels.INCOMING_LAYOUTS;
import static com.streamx.blueprints.composition.Channels.INCOMING_LAYOUTS_STATE;
import static com.streamx.blueprints.composition.Channels.INCOMING_PAGE_COMPOSE_REQUESTS;
import static com.streamx.blueprints.composition.Channels.OUTGOING_PAGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Composition;
import com.streamx.blueprints.data.Layout;
import com.streamx.blueprints.data.Page;
import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.test.unit.StatefulInMemorySource;
import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventAttributes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CompositionFunctionTest {

  private static final String ANY_CONTENT = "any-content";
  private static final String RESOURCE_TYPE = "any";

  private StatefulInMemorySource layoutsSource;
  private StatefulInMemorySource compositionsSource;
  private InMemorySource<CloudEvent> incomingPageComposeRequestsSource;
  private InMemorySink<CloudEvent> pagesSink;

  @InjectSpy
  CompositionFunction compositionFunction;

  @Inject
  @Any
  InMemoryConnector connector;

  @BeforeEach
  void init() {
    initInMemoryObjects();
    simulateEmittedPageComposeRequestIsSentToIncomingChannel();
  }

  private void initInMemoryObjects() {
    layoutsSource = new StatefulInMemorySource(connector,
        INCOMING_LAYOUTS, INCOMING_LAYOUTS_STATE);
    compositionsSource = new StatefulInMemorySource(connector,
        INCOMING_COMPOSITIONS, INCOMING_COMPOSITIONS_STATE);

    incomingPageComposeRequestsSource = connector.source(INCOMING_PAGE_COMPOSE_REQUESTS);

    pagesSink = connector.sink(OUTGOING_PAGES);
    pagesSink.clear();
  }

  private void simulateEmittedPageComposeRequestIsSentToIncomingChannel() {
    // CompositionFunction should be configured to use the same ref for channels for both incoming
    // and outgoing PageComposeRequests. Since InMemoryConnector operates on channels, not refs,
    // it's not able to perform that. Simulate this manually here:
    doAnswer(invocationOnMock -> {
      @SuppressWarnings("unchecked")
      var messages = (Multi<Message<CloudEvent>>) invocationOnMock.callRealMethod();
      messages.subscribe().asStream()
          .forEach(message -> incomingPageComposeRequestsSource.send(message.getPayload()));
      return Multi.createFrom().empty(); // original stream is now already consumed
    }).when(compositionFunction).consumeLayout(any());

    doAnswer(invocationOnMock -> {
      var event = (CloudEvent) invocationOnMock.callRealMethod();
      incomingPageComposeRequestsSource.send(event);
      return event;
    }).when(compositionFunction).consumeComposition(any());
  }

  record PublishedPage(String subject, String type, String pageContent, String pageType) {

    static PublishedPage of(String subject, String content, String pageType) {
      return new PublishedPage(subject, Page.TYPE_PUBLISHED, content, pageType);
    }

    static PublishedPage of(String subject, String content) {
      return of(subject, content, RESOURCE_TYPE);
    }
  }

  @Test
  void shouldComposePage() {
    // given
    String layoutKey = "layout-for-foobar-pages";
    String layout = """
        <html>
          Hello {{#insert name="foo.html"}}.
          This is {{#insert name="bar.html"}}.
          FAQs:
            {{#insert name="faq.html"}}
              No data
            {{}}
        </html>
        """;

    String compositionKey = "composition-for-john-streamx-foobar-page";
    String composition = """
        {{#define name="foo.html"}}
        <b>John</b>
        the
        developer

        {{#define name="bar.html"}}
        <b>StreamX</b>
        and how to use it
        """;

    // when
    publishLayout(layoutKey, layout);
    publishComposition(compositionKey, composition, layoutKey);

    // then
    assertSinglePublishedPage(PublishedPage.of(compositionKey, """
        <html>
          Hello <b>John</b>
        the
        developer.
          This is <b>StreamX</b>
        and how to use it.
          FAQs:
            No data
        </html>
        """));
  }


  @Test
  void shouldComposePage_WhenSegmentIsNotProvidedInComposition() {
    // given
    String layoutKey = "layout-for-hello-pages";
    String layout = """
        <html>
          Hello {{#insert name="name.html"}}
          {{}}.
        </html>
        """;

    String compositionKey = "composition-for-john-page";
    String composition = """
        {{#define name="foo.html"}}
        Content for foo.html. Note: content for name.html is not provided
        """;

    // when
    publishLayout(layoutKey, layout);
    publishComposition(compositionKey, composition, layoutKey);

    // then
    assertSinglePublishedPage(PublishedPage.of(compositionKey, """
        <html>
          Hello .
        </html>
        """));
  }

  @Test
  void shouldComposePage_UsingTheNewestLayoutVersion() {
    // given
    String layoutKey = "layout-1";
    publishLayout(layoutKey, "layout-type-1", "Hello, {{#insert name=\"name.html\"}}{{}}");
    publishLayout(layoutKey, "layout-type-2", "More hello, {{#insert name=\"name.html\"}}{{}}");
    publishLayout(layoutKey, "layout-type-3",
        "Even more hello, {{#insert name=\"name.html\"}}{{}}");

    // when
    String compositionKey = "composition-1";
    publishComposition(compositionKey, "{{#define name=\"name.html\"}}World", layoutKey);

    // then
    assertSinglePublishedPage(PublishedPage.of(compositionKey,
        "Even more hello, World", "layout-type-3"));
  }

  @Test
  void shouldRecomposePages_CreatedFromUpdatedLayout() {
    // given: publish a layout and two compositions that use it
    String layoutKey = "greetings-layout";
    publishLayout(layoutKey, "Hello, {{#insert name=\"name.html\"}}{{}}");

    String composition1Key = "hello-john";
    publishComposition(composition1Key, "{{#define name=\"name.html\"}}John", layoutKey);

    String composition2Key = "hello-kate";
    publishComposition(composition2Key, "{{#define name=\"name.html\"}}Kate", layoutKey);

    // publish also a composition that uses different layout
    String composition3Key = "hi-mark";
    publishComposition(composition3Key, "{{#define name=\"name.html\"}}Mark", "hi-layout");

    // expect 2 pages to be published
    assertPublishedPages(
        PublishedPage.of(composition1Key, "Hello, John"),
        PublishedPage.of(composition2Key, "Hello, Kate")
    );

    pagesSink.clear();

    // when: publish modified layout
    publishLayout(layoutKey, "Good morning, {{#insert name=\"name.html\"}}{{}}");

    // then: expect the two pages to be recomposed
    assertPublishedPages(
        PublishedPage.of(composition1Key, "Good morning, John"),
        PublishedPage.of(composition2Key, "Good morning, Kate")
    );
  }

  @Test
  void shouldUnpublishComposedPages_WhenTheirLayoutIsUnpublished() {
    // given: publish two compositions that use layout A
    String layoutA = "layout-A";
    publishLayout(layoutA, "Hello, {{#insert name=\"name.html\"}}{{}}");

    String compositionA1 = "composition-A1";
    publishComposition(compositionA1, "{{#define name=\"name.html\"}}A1", layoutA);

    String compositionA2 = "composition-A2";
    publishComposition(compositionA2, "{{#define name=\"name.html\"}}A2", layoutA);

    // and: publish three compositions that use layout B
    String layoutB = "layout-B";
    publishLayout(layoutB, "Goodbye, {{#insert name=\"name.html\"}}{{}}");

    String compositionB1 = "composition-B1";
    publishComposition(compositionB1, "{{#define name=\"name.html\"}}B1", layoutB);

    String compositionB2 = "composition-B2";
    publishComposition(compositionB2, "{{#define name=\"name.html\"}}B2", layoutB);

    String compositionB3 = "composition-B3";
    publishComposition(compositionB3, "{{#define name=\"name.html\"}}B3", layoutB);

    // expect all the pages to be published
    assertPublishedPages(
        PublishedPage.of(compositionA1, "Hello, A1"),
        PublishedPage.of(compositionA2, "Hello, A2"),
        PublishedPage.of(compositionB1, "Goodbye, B1"),
        PublishedPage.of(compositionB2, "Goodbye, B2"),
        PublishedPage.of(compositionB3, "Goodbye, B3")
    );

    pagesSink.clear();

    // when
    unpublishLayout(layoutB);

    // then: expect the pages composed using the unpublished layout, to be unpublished
    assertUnpublishedPages(compositionB1, compositionB2, compositionB3);
  }

  @Test
  void shouldNotComposePages_WhenLayoutIsMissing_AndComposeThem_WhenLayoutIsPublished() {
    // given
    String layoutKey = "title-page-layout";
    String activitiesCompositionKey = "activities-page";
    String carsCompositionKey = "cars-page";

    // when
    publishComposition(activitiesCompositionKey, "{{#define name=\"title.html\"}}Activities",
        layoutKey);
    publishComposition(carsCompositionKey, "{{#define name=\"title.html\"}}Cars", layoutKey);

    // then
    assertNoOutgoingPageEvents();

    // when
    publishLayout(layoutKey, "Title of {{#insert name=\"title.html\"}} page");

    // then
    assertPublishedPages(
        PublishedPage.of(activitiesCompositionKey, "Title of Activities page"),
        PublishedPage.of(carsCompositionKey, "Title of Cars page")
    );
  }

  @Test
  void shouldNotComposePage_WhenLayoutWasUnpublished() {
    // given
    String layoutKey = "layout-2";
    String compositionKey = "composition-2";

    publishLayout(layoutKey, ANY_CONTENT);

    // when
    unpublishLayout(layoutKey);
    publishComposition(compositionKey, ANY_CONTENT, layoutKey);

    // then
    assertNoOutgoingPageEvents();
  }

  @Test
  void shouldNotComposePage_WhenLayoutIsPublished_AfterCompositionWasUnpublished() {
    // given
    String layoutKey = "layout-3";
    String compositionKey = "composition-3";

    // and
    publishComposition(compositionKey, ANY_CONTENT, layoutKey);
    unpublishComposition(compositionKey);
    assertUnpublishedPages(compositionKey);
    pagesSink.clear();

    // when
    publishLayout(layoutKey, ANY_CONTENT);

    // then
    assertNoOutgoingPageEvents();
  }

  @Test
  void shouldUnpublishComposedPage_OnUnpublishCompositionMessage() {
    // given
    String layoutKey = "layout-4";
    String layoutContent = ANY_CONTENT;
    String layoutType = "layout-type";
    publishLayout(layoutKey, layoutType, layoutContent);

    String compositionKey = "composition-4";
    publishComposition(compositionKey, ANY_CONTENT, layoutKey);
    assertSinglePublishedPage(PublishedPage.of(compositionKey, layoutContent, layoutType));
    pagesSink.clear();

    // when
    unpublishComposition(compositionKey);

    // then
    assertUnpublishedPages(compositionKey);
  }

  @Test
  void shouldRecomposeMultiplePages_FromModifiedLayout() {
    // given: publish layout
    String layoutKey = "layout-for-multiple-compositions";
    String layout = "Hello, {{#insert name=\"name.html\"}}";
    publishLayout(layoutKey, layout);

    // and: publish initial compositions
    int numberOfCompositions = 100;
    for (int i = 0; i < numberOfCompositions; i++) {
      publishComposition("composition_" + i, "{{#define name=\"name.html\"}}User " + i, layoutKey);
    }

    // and: verify pages are composed
    assertPublishedPages(
        IntStream
            .range(0, numberOfCompositions)
            .mapToObj(i -> PublishedPage.of("composition_" + i, "Hello, User " + i))
            .toArray(PublishedPage[]::new)
    );
    pagesSink.clear();

    // when: publish modified layout
    String modifiedLayout = "Bye, {{#insert name=\"name.html\"}}";
    publishLayout(layoutKey, modifiedLayout);

    // then: verify pages are re-composed
    assertPublishedPages(
        IntStream
            .range(0, numberOfCompositions)
            .mapToObj(i -> PublishedPage.of("composition_" + i, "Bye, User " + i))
            .toArray(PublishedPage[]::new)
    );
  }

  @Test
  void shouldComposeRemainingPages_WhenComposingOneOfThemFails() {
    // given: publish layout
    String layoutKey = "layout-for-image";
    publishLayout(layoutKey, "<img src='{{#insert name=\"imageSrc\"}}' />");

    // when: publish compositions. Configure one of them to cause page composing failure
    doThrow(new IllegalStateException("Page composing failure"))
        .when(compositionFunction)
        .composePage(
            argThat(composition -> composition.getContentAsString().contains("image-3.png")),
            any(Layout.class)
        );

    for (int i = 1; i <= 5; i++) {
      publishComposition("composition_" + i, "{{#define name=\"imageSrc\"}}image-" + i + ".png",
          layoutKey);
    }

    // then: expect all compositions except composition_3 to result in composed pages
    assertPublishedPages(
        PublishedPage.of("composition_1", "<img src='image-1.png' />"),
        PublishedPage.of("composition_2", "<img src='image-2.png' />"),
        PublishedPage.of("composition_4", "<img src='image-4.png' />"),
        PublishedPage.of("composition_5", "<img src='image-5.png' />")
    );
  }

  private void publishLayout(String key, String content) {
    publishLayout(key, RESOURCE_TYPE, content);
  }

  private void publishLayout(String key, String type, String content) {
    Layout layout = new Layout(content, type);
    CloudEvent event = eventWithData(key, Layout.TYPE_PUBLISHED, layout);
    layoutsSource.send(event);
  }

  private void unpublishLayout(String key) {
    CloudEvent event = eventWithoutData(key, Layout.TYPE_UNPUBLISHED);
    layoutsSource.send(event);
  }

  private void publishComposition(String key, String content, String layoutKey) {
    Composition composition = new Composition(content, RESOURCE_TYPE, layoutKey);
    CloudEvent event = eventWithData(key, Composition.TYPE_PUBLISHED, composition);
    compositionsSource.send(event);
  }

  private void unpublishComposition(String key) {
    CloudEvent event = eventWithoutData(key, Composition.TYPE_UNPUBLISHED);
    compositionsSource.send(event);
  }

  @SafeVarargs
  private void send(CloudEvent event, InMemorySource<CloudEvent>... sources) {
    for (InMemorySource<CloudEvent> source : sources) {
      source.send(event);
    }
  }

  private void assertSinglePublishedPage(PublishedPage expectedPage) {
    assertPublishedPages(expectedPage);
  }

  private void assertPublishedPages(PublishedPage... expectedPages) {
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(pagesSink.received()).hasSameSizeAs(expectedPages)
    );

    List<PublishedPage> publishedPages = pagesSink.received().stream()
        .map(Message::getPayload)
        .map(event -> {
          Resource resource = CloudEventUtils.getData(event, Resource.class);
          return PublishedPage.of(event.getSubject(), resource.getContentAsString(),
              resource.getType());
        })
        .toList();

    assertThat(publishedPages).containsExactlyInAnyOrder(expectedPages);
  }

  private void assertUnpublishedPages(String... expectedSubjects) {
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(pagesSink.received()).hasSameSizeAs(expectedSubjects)
    );

    List<String> unpublishedSubjects = pagesSink.received().stream()
        .map(Message::getPayload)
        .map(CloudEventAttributes::getSubject)
        .toList();

    assertThat(unpublishedSubjects).containsExactlyInAnyOrder(expectedSubjects);
  }

  private void assertNoOutgoingPageEvents() {
    await().during(Duration.ofSeconds(1)).untilAsserted(() ->
        assertThat(pagesSink.received()).isEmpty()
    );
  }
}
