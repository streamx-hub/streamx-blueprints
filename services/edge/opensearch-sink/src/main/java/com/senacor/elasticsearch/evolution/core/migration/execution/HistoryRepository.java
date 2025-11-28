package com.senacor.elasticsearch.evolution.core.migration.execution;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.senacor.elasticsearch.evolution.core.MigrationException;
import com.senacor.elasticsearch.evolution.core.model.MigrationVersion;
import com.senacor.elasticsearch.evolution.core.model.dbhistory.MigrationScriptProtocol;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

public class HistoryRepository {

  private static final Logger logger = Logger.getLogger(HistoryRepository.class);
  private static final String INTERNAL_LOCK_VERSION = "0.1";
  private static final MigrationVersion INTERNAL_VERSIONS = MigrationVersion.fromVersion("0");

  private final RestClient restClient;
  private final String historyIndex = "es_evolution";
  private final MigrationScriptProtocolMapper migrationScriptProtocolMapper =
      new MigrationScriptProtocolMapper();
  private final ObjectMapper objectMapper;

  public HistoryRepository(RestClient restClient, ObjectMapper objectMapper) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
  }

  /**
   * @return sorted set by version. The earliest version is the first element and the latest version
   * is the last element.
   * @throws MigrationException in case the operation failed
   */
  public NavigableSet<MigrationScriptProtocol> findAll() throws MigrationException {
    try {
      final Request findAllSearchRequest = new Request("POST", "/" + historyIndex + "/_search");
      findAllSearchRequest.addParameters(indicesOptions(IndexOptions.lenientExpandOpen()));
      findAllSearchRequest.setJsonEntity("{\"size\":1000}");
      final Response searchResponse = restClient.performRequest(findAllSearchRequest);
      final String bodyAsString = EntityUtils.toString(searchResponse.getEntity());
      logger.debugf("findAll res: %s (body=%s)", searchResponse, bodyAsString);
      validateHttpStatusIs2xx(searchResponse, "findAll");

      final SearchResponse body = objectMapper.readValue(bodyAsString, SearchResponse.class);

      // map and order
      return body.hits().hitList().stream()
          .map(Hit::source)
          .map(migrationScriptProtocolMapper::mapFromMap)
          // filter protocols with 0 major version, because they are used internal
          .filter(protocol -> protocol.version().isMajorNewerThan(INTERNAL_VERSIONS))
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      throw new MigrationException("findAll failed!", e);
    }
  }

  /**
   * Put the protocol in the internal Elasticsearch-Evolution history index and use the version as
   * ID.
   *
   * @param migrationScriptProtocol the protocol to save or update
   * @throws MigrationException in case the operation failed
   */
  public void saveOrUpdate(MigrationScriptProtocol migrationScriptProtocol)
      throws MigrationException {
    try {
      final String id = requireNonNull(migrationScriptProtocol.version(),
          "migrationScriptProtocol.version must not be null").getVersion();
      final Request indexRequest = new Request("PUT", "/" + historyIndex + "/_doc/" + id);
      final Map<String, Object> source = migrationScriptProtocolMapper.mapToMap(
          migrationScriptProtocol);
      indexRequest.setJsonEntity(objectMapper.writeValueAsString(source));
      final Response res = restClient.performRequest(indexRequest);

      logger.debugf("saveOrUpdate res: %s (body=%s)", res, EntityUtils.toString(res.getEntity()));
      validateHttpStatusIs2xx(res, "saveOrUpdate");
    } catch (IOException e) {
      throw new MigrationException(
          "saveOrUpdate of '%s' failed!".formatted(migrationScriptProtocol), e);
    }
  }

  /**
   * @return true, if the index is locked and Elasticsearch-Evolution has to wait until the lock is
   * released.
   * @throws MigrationException in case the check failed
   */
  public boolean isLocked() throws MigrationException {
    try {
      refresh(historyIndex);

      final String countQuery =
          "{\"query\":{\"term\":{\"" + MigrationScriptProtocolMapper.LOCKED_FIELD_NAME
          + "\":{\"value\":true}}}}";
      final long count = executeCountRequest(Optional.of(countQuery));

      if (count == 0L) {
        logger.debugf("index '%s' is not locked: no locked documents in index.", historyIndex);
        return false;
      }

      logger.debugf("index '%s' is locked: {} locked documents found.", historyIndex, count);
      return true;
    } catch (IOException e) {
      throw new MigrationException("isLocked check failed!", e);
    }
  }

  private long executeCountRequest(Optional<String> countQuery) throws IOException {
    final Request countRequest = new Request("GET", "/" + historyIndex + "/_count");
    countRequest.addParameters(indicesOptions(IndexOptions.lenientExpandOpen()));
    countQuery.ifPresent(countRequest::setJsonEntity);
    final Response countResponse = restClient.performRequest(countRequest);

    validateHttpStatusIs2xx(countResponse, "isLocked");

    final JsonNode countResBody = objectMapper.readTree(countResponse.getEntity().getContent());
    return countResBody.get("count").asLong();
  }

  /**
   * This will lock the index for other Elasticsearch-Evolution instances
   *
   * @return true, if the lock was set successfully.
   */
  public boolean lock() {
    try {
      final long countAll = executeCountRequest(Optional.empty());
      if (countAll == 0L) {
        saveOrUpdate(new MigrationScriptProtocol()
            .setVersion(INTERNAL_LOCK_VERSION)
            .setScriptName("-")
            .setDescription("lock entry")
            .setExecutionRuntimeInMillis(0)
            .setSuccess(true)
            .setChecksum(0)
            .setExecutionTimestamp(OffsetDateTime.now())
            .setIndexName(historyIndex)
            .setLocked(true));
      } else {
        executeLockRequest(true, "lock");
      }
      return true;
    } catch (IOException e) {
      logger.warn("lock failed", e);
      return false;
    }
  }

  /**
   * This will unlock the index for other Elasticsearch-Evolution instances
   *
   * @return true, if the unlock was successfully.
   */
  public boolean unlock() {
    try {
      refresh(historyIndex);

      final Request updateByQueryRequest = new Request("POST",
          "/" + historyIndex + "/_update_by_query");
      updateByQueryRequest.addParameters(indicesOptions(IndexOptions.lenientExpandOpen()));
      updateByQueryRequest.addParameter("requests_per_second", "-1");
      updateByQueryRequest.addParameter("refresh", "true");
      updateByQueryRequest.setJsonEntity("{\"script\":{"
                                         + "\"source\":\"ctx.op = \\\"delete\\\"\","
                                         + "\"lang\":\"painless\"},"
                                         + "\"size\":1000,"
                                         + "\"query\":{\"term\":{\""
                                         + MigrationScriptProtocolMapper.VERSION_FIELD_NAME
                                         + "\":{\"value\":\"" + INTERNAL_LOCK_VERSION + "\"}}}}");

      final Response deleteInternalLockRes = restClient.performRequest(updateByQueryRequest);

      logger.debugf("unlock.deleteLockEntry res: %s (body=%s)", deleteInternalLockRes,
          EntityUtils.toString(deleteInternalLockRes.getEntity()));

      executeLockRequest(false, "unlock.removeLock");
      return true;
    } catch (IOException e) {
      logger.warn("unlock failed", e);
      return false;
    }
  }

  private void executeLockRequest(boolean lock, String debugContext) throws IOException {
    final Request updateByQueryRequest = new Request("POST",
        "/" + historyIndex + "/_update_by_query");
    updateByQueryRequest.addParameters(indicesOptions(IndexOptions.lenientExpandOpen()));
    updateByQueryRequest.addParameter("requests_per_second", "-1");
    updateByQueryRequest.addParameter("refresh", "true");
    updateByQueryRequest.setJsonEntity("{\"script\":"
                                       + "{\"source\":\"ctx._source."
                                       + MigrationScriptProtocolMapper.LOCKED_FIELD_NAME
                                       + " = params.lock\","
                                       + "\"lang\":\"painless\","
                                       + "\"params\":{\"lock\":" + lock + "}"
                                       + "},"
                                       + "\"size\":1000,"
                                       + "\"query\":{\"term\":{\""
                                       + MigrationScriptProtocolMapper.LOCKED_FIELD_NAME
                                       + "\":{\"value\":" + !lock + "}}}}");

    final Response updateByQueryResponse = restClient.performRequest(updateByQueryRequest);

    logger.debugf("%s res: %s (body=%s)", debugContext, updateByQueryResponse,
        EntityUtils.toString(updateByQueryResponse.getEntity()));
  }

  /**
   * Creates the internal elasticsearch-evolution history index in Elasticsearch if necessary.
   *
   * @return true, if the index was created, false if it's already present in Elasticsearch
   * @throws MigrationException in case the operation failed
   */
  public boolean createIndexIfAbsent() throws MigrationException {
    try {
      Response existsRes = restClient.performRequest(new Request("HEAD", "/" + historyIndex));
      boolean exists = 200 == existsRes.getStatusLine().getStatusCode();
      if (exists) {
        logger.debugf("Elasticsearch-Evolution history index '%s' already exists.", historyIndex);
        return false;
      }
      logger.debugf("Elasticsearch-Evolution history index '%s' does not yet exists. Res=%s",
          historyIndex, existsRes);

      // create index
      Response createRes = restClient.performRequest(new Request("PUT", "/" + historyIndex));
      if (hasNotStatusCode2xx(createRes)) {
        throw new IllegalStateException(
            "Could not create Elasticsearch-Evolution history index '" + historyIndex
            + "'. Create res=" + createRes);
      }
      logger.debugf("created Elasticsearch-Evolution history index '%s'", historyIndex);
      return true;
    } catch (IOException e) {
      throw new MigrationException("createIndexIfAbsent failed!", e);
    }
  }

  private boolean hasNotStatusCode2xx(Response response) {
    return isNotStatusCode2xx(response.getStatusLine().getStatusCode());
  }

  private boolean isNotStatusCode2xx(int statusCode) {
    return statusCode < 200 || statusCode > 299;
  }

  private void validateHttpStatusIs2xx(Response response, String description)
      throws MigrationException {
    validateHttpStatusIs2xx(response.getStatusLine().getStatusCode(),
        description + " (" + response.getStatusLine().getReasonPhrase() + ")");
  }

  void validateHttpStatusIs2xx(int statusCode, String description) throws MigrationException {
    if (isNotStatusCode2xx(statusCode)) {
      throw new MigrationException(
          "%s - response status is not OK: %s".formatted(description, statusCode));
    }
  }

  /**
   * refresh the index to get all pending documents in the index which are currently in the indexing
   * process. This is a bit like a flush in JPA.
   */
  void refresh(String... indices) {
    try {
      final Request refreshRequest = new Request("GET",
          "/" + expandIndicesForUrl(indices) + "/_refresh");
      refreshRequest.addParameters(indicesOptions(IndexOptions.lenientExpandOpen()));

      Response res = restClient.performRequest(refreshRequest);

      validateHttpStatusIs2xx(res, "refresh");
    } catch (IOException e) {
      throw new MigrationException("refresh failed!", e);
    }
  }

  private String expandIndicesForUrl(String... indices) {
    return String.join(",", indices);
  }

  private Map<String, String> indicesOptions(IndexOptions indicesOptions) {
    Map<String, String> nameValuePairs = new HashMap<>();
    nameValuePairs.put("ignore_unavailable", Boolean.toString(indicesOptions.ignoreUnavailable()));
    nameValuePairs.put("allow_no_indices", Boolean.toString(indicesOptions.allowNoIndices()));
    String expandWildcards;
    if (!indicesOptions.expandWildcardsOpen() && !indicesOptions.expandWildcardsClosed()) {
      expandWildcards = "none";
    } else {
      StringJoiner joiner = new StringJoiner(",");
      if (indicesOptions.expandWildcardsOpen()) {
        joiner.add("open");
      }
      if (indicesOptions.expandWildcardsClosed()) {
        joiner.add("closed");
      }
      expandWildcards = joiner.toString();
    }
    nameValuePairs.put("expand_wildcards", expandWildcards);
    return nameValuePairs;
  }

  private record IndexOptions(boolean ignoreUnavailable, boolean allowNoIndices,
                              boolean expandWildcardsOpen, boolean expandWildcardsClosed) {

    private static final IndexOptions LENIENT_EXPAND_OPEN = new IndexOptions(
        true,
        true,
        true,
        false);

    public static IndexOptions lenientExpandOpen() {
      return LENIENT_EXPAND_OPEN;
    }
  }

  record SearchResponse(long took, @JsonProperty("timed_out") boolean timedOut, Hits hits) {

  }

  record Hits(TotalHits total, @JsonProperty("max_score") BigDecimal maxScore,
              @JsonProperty("hits") List<Hit> hitList) {

  }

  /**
   * @param relation Values of relation: - "eq" = Accurate - "gte" = "Lower bound, including
   *                 returned documents"
   */
  record TotalHits(long value, String relation) {

  }

  record Hit(@JsonProperty("_id") String id, @JsonProperty("_source") Map<String, Object> source) {

  }
}