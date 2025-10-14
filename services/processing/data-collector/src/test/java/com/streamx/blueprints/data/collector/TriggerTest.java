package com.streamx.blueprints.data.collector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.streamx.blueprints.data.collector.collectors.Collector.CollectedOutput;
import com.streamx.blueprints.data.collector.configuration.ServiceConfigMapping;
import dev.streamx.blueprints.data.Data;
import dev.streamx.metadata.Properties;
import dev.streamx.quasar.reactive.messaging.metadata.Action;
import dev.streamx.quasar.reactive.messaging.metadata.EventTime;
import dev.streamx.quasar.reactive.messaging.metadata.Key;
import java.util.List;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TriggerTest {

  @Mock
  private Emitter<Data> dataEmitter;

  @Mock
  private Logger log;

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
    when(collectors.processData(any(), any(), any(), any())).thenReturn(true);
  }

  void mockCollectorsReturningCollectedData() {
    when(collectors.collect()).thenReturn(
        List.of(new CollectedOutput(Key.of("collected-data"), new Data("any-content"))));
  }

  void verifyCollectedDataEmitted() {
    verify(dataEmitter).send(ArgumentMatchers.<Message>any());
  }

  private void simulateProcessingDataEvent() {
    cut.process(new Data("any-content"),
        Key.of("any-key"),
        Action.PUBLISH,
        EventTime.of(1L),
        Properties.empty());
  }

  private void initializeMaxDirtySequenceCount(Long value) {
    when(serviceConfigMapping.dirtyCheck()).thenReturn(dirtyCheck);
    when(webResourcesService.isMatchingFilter(any())).thenReturn(false);
    when(dirtyCheck.maxDirtySequenceCount()).thenReturn(value);
  }
}