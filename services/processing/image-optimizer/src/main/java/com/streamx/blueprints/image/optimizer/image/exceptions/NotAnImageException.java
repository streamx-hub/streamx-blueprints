package com.streamx.blueprints.image.optimizer.image.exceptions;

import java.io.IOException;

public class NotAnImageException extends IOException {

  public NotAnImageException(String message) {
    super(message);
  }

  public NotAnImageException(String message, Throwable cause) {
    super(message, cause);
  }

}
