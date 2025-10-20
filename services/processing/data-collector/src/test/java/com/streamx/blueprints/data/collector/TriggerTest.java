package com.streamx.blueprints.data.collector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.cloudevents.utils.CloudEventUtils;
import com.streamx.blueprints.data.Data;
import com.streamx.blueprints.data.collector.collectors.Collector.CollectedOutput;
import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping;
import com.streamx.blueprints.data.collector.stores.PublishedDataStore;
import io.cloudevents.CloudEvent;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggerTest {

  @Mock
  private Emitter<CloudEvent> dataEmitter;

  @Mock
  private Collectors collectors;

  @Mock
  private ServiceConfigMapping serviceConfigMapping;

  @Mock
  private ServiceConfigMapping.DirtyCheck dirtyCheck;

  @Mock
  private WebResourcesService webResourcesService;

  @InjectMocks
  private ProcessDataFunction cut;

  @BeforeAll
  static void setApplicationName() {
    System.setProperty("quarkus.application.name", TriggerTest.class.getSimpleName());
  }

  @BeforeEach
  void injectIrrelevantMocks() {
    cut.log = mock(Logger.class);
    cut.dataStore = mock(PublishedDataStore.class);
  }

  @Test
  void shouldNotTriggerGeneration() {
    // when
    cut.trigger();

    // then
    verifyNoInteractions(dataEmitter);
  }

  @Test
  void shouldOmitTriggerGenerationIfMaxAllowedDirtySequenceIsNotExceeded() {
    // given
    initializeMaxDirtySequenceCount(1L);
    mockCollectorsAcceptingAnyData();
    simulateProcessingDataEvent();

    // when
    cut.trigger();

    // then
    verifyNoInteractions(dataEmitter);
  }

  @Test
  void shouldTriggerGenerationIfMaxAllowedDirtySequenceIsExceeded() {
    // given
    initializeMaxDirtySequenceCount(0L);
    mockCollectorsAcceptingAnyData();
    mockCollectorsReturningCollectedData();
    simulateProcessingDataEvent();

    // when
    cut.trigger();

    // then
    verifyCollectedDataEmitted();
  }

  @Test
  void shouldTriggerGeneration() {
    // given
    initializeMaxDirtySequenceCount(1L);
    mockCollectorsAcceptingAnyData();
    mockCollectorsReturningCollectedData();
    simulateProcessingDataEvent();
    cut.trigger();

    // when
    cut.trigger();

    // then
    verifyCollectedDataEmitted();
  }

  void mockCollectorsAcceptingAnyData() {
    when(collectors.processData(any(), any(), any())).thenReturn(true);
  }

  void mockCollectorsReturningCollectedData() {
    when(collectors.collect()).thenReturn(
        List.of(new CollectedOutput("collected-data", "any-content")));
  }

  void verifyCollectedDataEmitted() {
    verify(dataEmitter).send(any(CloudEvent.class));
  }

  private void simulateProcessingDataEvent() {
    cut.process(CloudEventUtils.eventWithData(
        "any-key",
        Data.TYPE_PUBLISHED,
        new Data("any-content"),
        CloudEventUtils.toOffsetDateTime(1)
    ));
  }

  private void initializeMaxDirtySequenceCount(Long value) {
    when(serviceConfigMapping.dirtyCheck()).thenReturn(dirtyCheck);
    when(webResourcesService.isMatchingFilter(any())).thenReturn(false);
    when(dirtyCheck.maxDirtySequenceCount()).thenReturn(value);
  }
}