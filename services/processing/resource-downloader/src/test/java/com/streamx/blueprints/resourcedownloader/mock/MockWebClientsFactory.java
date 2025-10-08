package com.streamx.blueprints.resourcedownloader.mock;

import static org.mockito.Mockito.mock;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.apache.http.impl.client.CloseableHttpClient;


@Dependent
public class MockWebClientsFactory {

  private static final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

  @Produces
  @IfBuildProfile(value = "repeating-test-download")
  public CloseableHttpClient httpClient() {
    return httpClient;
  }

}
