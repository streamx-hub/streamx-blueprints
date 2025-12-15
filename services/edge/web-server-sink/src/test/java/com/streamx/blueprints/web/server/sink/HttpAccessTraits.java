package com.streamx.blueprints.web.server.sink;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.StringContains.containsString;

import java.time.Duration;
import org.awaitility.core.ThrowingRunnable;

interface HttpAccessTraits {

  default void assertCanAccessViaHttp(String path, String content) {
    waitUntilAsserted(() ->
        given().basePath("/")
            .when()
            .get(path)
            .then()
            .statusCode(200)
            .body(containsString(content)));
  }

  default void assertCannotAccessViaHttp(String path) {
    waitUntilAsserted(() ->
        given().basePath("/")
            .when()
            .get(path)
            .then()
            .statusCode(404)
    );
  }

  private static void waitUntilAsserted(ThrowingRunnable assertion) {
    await().atMost(Duration.ofSeconds(3)).untilAsserted(assertion);
  }

}
