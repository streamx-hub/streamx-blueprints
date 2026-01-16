package com.streamx.blueprints.state;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import org.eclipse.microprofile.config.Config;

public abstract class BaseConfigTest {

  protected final Config config = mock(Config.class);

  protected void setConfigProperty(String name, String value) {
    doReturn(Optional.ofNullable(value))
        .when(config)
        .getOptionalValue(name, String.class);
  }
}