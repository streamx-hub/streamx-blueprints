package dev.streamx.blueprints.opensearch.delivery.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.streamx.blueprints.opensearch.delivery.index.model.DefaultDocument;
import dev.streamx.blueprints.opensearch.delivery.index.model.Fragment;
import dev.streamx.blueprints.opensearch.delivery.index.model.SearchIndexStorageException;
import dev.streamx.blueprints.opensearch.delivery.utils.ExceptionUtils;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DefaultRepository {

  public static final String DEFAULT_INDEX_NAME = "default";

  @Inject
  Logger log;

  @Inject
  RestClient client;

  @Inject
  ObjectMapper objectMapper;

  public Uni<JsonNode> searchByTemplate(String searchTemplateId,
      MultivaluedMap<String, String> queryParameters) {
    if (log.isTraceEnabled()) {
      log.tracef("Search by %s template with params: %s", searchTemplateId, queryParameters);
    }
    String requestBody = createSearchByTemplateRequestBody(searchTemplateId,
        queryParameters);
    return searchByTemplate(searchTemplateId, requestBody);
  }

  public Uni<JsonNode> searchByTemplate(String searchTemplateId,
      String requestBody) {
    String endpoint = DEFAULT_INDEX_NAME + "/_search/template";

    Request request = new Request("GET", endpoint);
    request.setJsonEntity(requestBody);

    if (log.isTraceEnabled()) {
      log.tracef("Search by %s template with request body: %s", searchTemplateId, requestBody);
    }

    return sendAsync(request)
        .map(response -> mapResponseToJson(request, response));
  }

  private String createSearchByTemplateRequestBody(String searchTemplateId,
      MultivaluedMap<String, String> queryParameters) {
    ObjectNode objectNode = objectMapper.createObjectNode();
    objectNode.set("id", new TextNode(searchTemplateId));

    ObjectNode params = objectMapper.createObjectNode();
    for (String paramName : queryParameters.keySet()) {
      var param = Optional.ofNullable(queryParameters.getFirst(paramName))
          .map(String::toLowerCase)
          .map(TextNode::new)
          .map(textNode -> (JsonNode) textNode)
          .orElse(NullNode.getInstance());
      params.set(paramName, param);
    }
    objectNode.set("params", params);

    return objectNode.toString();
  }

  public Uni<Void> index(String path, DefaultDocument defaultDocument) {
    return Uni.createFrom().item(() -> createBulkIndexCommand(path, defaultDocument))
        .flatMap(this::executeBulk);
  }

  private String createBulkIndexCommand(String path, DefaultDocument defaultDocument) {
    try {
      String indexCommand = objectMapper.writeValueAsString(
          new IndexCommand(new IndexEntry(DEFAULT_INDEX_NAME, path))
      );
      String payload = objectMapper.writeValueAsString(defaultDocument);
      return """
          %s
          %s
          """.formatted(indexCommand, payload);
    } catch (JsonProcessingException e) {
      throw ExceptionUtils.sneakyThrow(e);
    }
  }

  public Uni<Void> deleteFromIndex(String path) {
    return Uni.createFrom().item(() -> createBulkDeleteCommand(path))
        .flatMap(this::executeBulk);
  }

  private String createBulkDeleteCommand(String path) {
    try {
      var deleteCommand = objectMapper.writeValueAsString(
          new DeleteCommand(new IndexEntry(DEFAULT_INDEX_NAME, path))
      );
      return """
          %s
          """.formatted(deleteCommand);
    } catch (JsonProcessingException e) {
      throw ExceptionUtils.sneakyThrow(e);
    }
  }

  private Uni<Void> executeBulk(String jsonEntity) {
    String endpoint = DEFAULT_INDEX_NAME + "/_bulk";
    Request request = new Request("POST", endpoint);

    request.setJsonEntity(jsonEntity);

    return sendAsync(request)
        .map(response -> mapResponseToJson(request, response))
        .replaceWithVoid();
  }

  public Uni<Void> refresh() {
    String endpoint = DEFAULT_INDEX_NAME + "/_refresh";
    Request request = new Request("POST", endpoint);

    return sendAsync(request)
        .map(response -> mapResponseToJson(request, response))
        .replaceWithVoid();
  }

  public Uni<UpdateResult> updateFragments(Fragment fragment) {
    String endpoint = DEFAULT_INDEX_NAME + "/_update_by_query";
    Request request = new Request("POST", endpoint);

    try {
      var key = objectMapper.writeValueAsString(fragment.key());
      var eventTime = objectMapper.writeValueAsString(fragment.eventTime());
      var payload = fragment.payload();

      var querySection = createUpdateSection(key, eventTime);
      var jsonEntity = createUpdateFragmentsByQueryCommand(querySection, key, eventTime, payload);

      request.setJsonEntity(jsonEntity);
      request.addParameter("conflicts", "proceed");

      return sendAsync(request)
          .map(response -> mapResponseToJson(request, response))
          .map(response -> objectMapper.convertValue(response, UpdateResult.class));
    } catch (JsonProcessingException e) {
      throw ExceptionUtils.sneakyThrow(e);
    }
  }

  private static String createUpdateFragmentsByQueryCommand(String querySection,
      String key, String eventTime, String payload) {
    return """
        {
          "query": %s,
          "script" : {
            "id": "updateFragments",
            "params" : {
              "key" : %s,
              "eventTime": %s,
              "payload": %s
            }
          }
        }
        """.formatted(querySection, key, eventTime, payload);
  }

  private static String createUpdateSection(String key, String eventTime) {
    return """
        {
            "bool": {
              "must": [{
                "nested": {
                  "path": "fragments",
                  "query": {
                    "bool": {
                      "must": [{
                        "term": {
                          "fragments.key": %s
                        }
                      }, {
                        "range": {
                          "fragments.eventTime": {
                            "lt": %s
                          }
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }""".formatted(key, eventTime);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record UpdateResult(
      long took,
      long total,
      long updated,
      long deleted,
      long batches,
      @JsonProperty("version_conflicts") long versionConflicts,
      List<JsonNode> failures
  ) { }

  private JsonNode mapResponseToJson(Request request, Response response) {
    try {
      String responseBody = EntityUtils.toString(response.getEntity());

      JsonNode jsonNode = objectMapper.readTree(responseBody);

      boolean timedOut = Optional.ofNullable(jsonNode.get("timed_out"))
          .map(JsonNode::asBoolean)
          .orElse(false);
      if (timedOut) {
        throw new SearchIndexStorageException(
            "Request to " + request.getEndpoint() + " timed out.");
      }

      return jsonNode;
    } catch (IOException e) {
      throw new RuntimeException("Corrupted Opensearch response.", e);
    }
  }

  private Uni<Response> sendAsync(Request request) {
    var completableFuture = new CompletableFuture<Response>();

    var responseListener = new CompletableFutureResponseListener(completableFuture);
    var cancellable = client.performRequestAsync(request, responseListener);

    return Uni.createFrom().completionStage(completableFuture)
        .onCancellation().invoke(cancellable::cancel);
  }

  private static class CompletableFutureResponseListener implements ResponseListener {

    private final CompletableFuture<Response> completableFuture;

    private CompletableFutureResponseListener(CompletableFuture<Response> completableFuture) {
      this.completableFuture = completableFuture;
    }

    @Override
    public void onSuccess(Response response) {
      completableFuture.complete(response);
    }

    @Override
    public void onFailure(Exception e) {
      completableFuture.completeExceptionally(e);
    }
  }

  private record IndexEntry(@JsonProperty("_index") String indexName,
                            @JsonProperty("_id") String key) {

  }

  private record IndexCommand(@JsonProperty("index") IndexEntry indexEntry) {

  }

  private record DeleteCommand(@JsonProperty("delete") IndexEntry indexEntry) {

  }
}
