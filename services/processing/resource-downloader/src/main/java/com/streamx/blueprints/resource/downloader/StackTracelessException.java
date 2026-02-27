package com.streamx.blueprints.resource.downloader;

public class StackTracelessException extends Exception {

  public StackTracelessException(String errorMessagePrefix, Exception cause) {
    super("%s - %s: %s".formatted(
        errorMessagePrefix,
        cause.getClass().getName(),
        cause.getMessage()
    ));
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this; // disables stack trace creation
  }
}
