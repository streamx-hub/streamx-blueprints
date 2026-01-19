package com.streamx.blueprints.state;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

public abstract class BaseConfigTest {

  private final MockedStatic<ConfigProvider> configProvider = mockStatic(ConfigProvider.class);
  protected final Config config = mock(Config.class);

  @BeforeEach
  void setupConfigProviderMock() {
    configProvider.when(ConfigProvider::getConfig).thenReturn(config);
  }

  @AfterEach
  void releaseConfigProviderMock() {
    configProvider.close();
  }

  protected void setConfigProperty(String name, String value) {
    doReturn(Optional.ofNullable(value))
        .when(config)
        .getOptionalValue(name, String.class);
  }

  protected String getConfigProperty(String name) {
    return config.getOptionalValue(name, String.class).orElseThrow();
  }
}