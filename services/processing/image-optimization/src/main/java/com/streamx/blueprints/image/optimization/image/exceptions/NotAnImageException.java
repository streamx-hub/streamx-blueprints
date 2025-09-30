package com.streamx.blueprints.image.optimization.image.exceptions;

import java.io.IOException;

public class NotAnImageException extends IOException {

  public NotAnImageException(String message) {
    super(message);
  }

}
