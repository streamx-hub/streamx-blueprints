package com.streamx.blueprints.resource.downloader;

public class DownloadException extends Exception {

  public DownloadException(String message) {
    super(message);
  }

  public DownloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
