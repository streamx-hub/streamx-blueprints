package com.senacor.elasticsearch.evolution.core.model.migration;

import static java.util.Objects.requireNonNull;

import com.senacor.elasticsearch.evolution.core.MigrationException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.http.entity.ContentType;

/**
 * Represents the HTTP request from the migration script
 */
public class MigrationScriptRequest {

  private static final String HEADER_NAME_CONTENT_TYPE = "Content-Type";

  private HttpMethod httpMethod;

  /**
   * relative path to the endpoint without hostname, like /my_index
   */
  private String path;

  private final Map<String, String> httpHeader = new HashMap<>();
  private final StringBuilder body = new StringBuilder();

  public HttpMethod getHttpMethod() {
    return httpMethod;
  }

  public MigrationScriptRequest setHttpMethod(HttpMethod httpMethod) {
    this.httpMethod = httpMethod;
    return this;
  }

  public String getPath() {
    return path;
  }

  public MigrationScriptRequest setPath(String path) {
    this.path = path;
    return this;
  }

  public Map<String, String> getHttpHeader() {
    return httpHeader;
  }

  public MigrationScriptRequest addHttpHeader(String header, String value) {
    this.httpHeader.put(header, value);
    return this;
  }

  public String getBody() {
    return body.toString();
  }

  public MigrationScriptRequest addToBody(String bodyPart) {
    this.body.append(bodyPart);
    return this;
  }

  public boolean isBodyEmpty() {
    return body.isEmpty();
  }

  public Optional<ContentType> getContentType() {
    return httpHeader.entrySet()
        .stream()
        .filter(entry -> HEADER_NAME_CONTENT_TYPE.equalsIgnoreCase(entry.getKey()))
        .map(entry -> ContentType.parse(entry.getValue()))
        .findFirst();
  }

  public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    OPTIONS,
    PATCH;

    public static HttpMethod create(String method) throws MigrationException {
      String normalizedMethod = requireNonNull(method, "method must not be null")
          .toUpperCase()
          .trim();
      return Arrays.stream(values())
          .filter(m -> m.name().equals(normalizedMethod))
          .findFirst()
          .orElseThrow(() -> new MigrationException(
              "Method '%s' not supported, only %s is supported.".formatted(
                  method, Arrays.toString(values()))));
    }
  }
}
