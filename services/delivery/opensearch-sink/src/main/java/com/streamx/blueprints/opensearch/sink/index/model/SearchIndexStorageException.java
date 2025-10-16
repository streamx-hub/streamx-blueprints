package com.streamx.blueprints.opensearch.sink.index.model;

public class SearchIndexStorageException extends RuntimeException {

  public SearchIndexStorageException(String message) {
    super(message);
  }

  public SearchIndexStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
