package com.streamx.blueprints.opensearch.sink.opensearch;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

import com.streamx.blueprints.opensearch.BaseOpensearchTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DefaultRepositoryTest extends BaseOpensearchTest {

  @Test
  void testSearchByTemplateWithRequestParams() {
    given()
        .when().get("/search/query?query=test")
        .then().statusCode(200)
        .body("hits.total.value", is(0))
        .body("timed_out", is(false))
        .body("took", greaterThan(0));
  }

  @Test
  void testSearchByTemplateWithRequestBody() {
    var requestBody = """
          {
          "id" : "query",
          "params" : {
            "query" : "test"
          }
        }""";
    given()
        .contentType("application/json")  //another way to specify content type
        .body(requestBody)
        .when()
        .post("/search/query/body")
        .then().statusCode(200)
        .body("hits.total.value", is(0))
        .body("timed_out", is(false))
        .body("took", greaterThan(0));
  }

}
