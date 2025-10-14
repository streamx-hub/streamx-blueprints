package com.streamx.blueprints.sitemap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DirtySequenceStateManagerTest {

  @Test
  void noneActionWhenNoDirtyResources() {
    DirtySequenceStateManager manager = new DirtySequenceStateManager(1L);
    // when
    boolean isActionNeeded = manager.checkIfActionIsNeededForNewSequence();

    // then
    assertFalse(isActionNeeded, "Action should be not needed when no dirty resources");
  }

  @Test
  void shouldHandleMaxAllowedDirtySequenceLimit() {
    // given
    DirtySequenceStateManager manager = new DirtySequenceStateManager(2L);

    // when
    // sequence 1
    manager.newDirtyResource();
    boolean isActionNeededSequence1 = manager.checkIfActionIsNeededForNewSequence();
    // sequence 2
    manager.newDirtyResource();
    boolean isActionNeededSequence2 = manager.checkIfActionIsNeededForNewSequence();
    // sequence 3
    manager.newDirtyResource();
    boolean isActionNeededSequence3 = manager.checkIfActionIsNeededForNewSequence();

    // then
    assertFalse(isActionNeededSequence1, "Action should be not needed if limit is not exceeded");
    assertFalse(isActionNeededSequence2, "Action should be not needed if limit is not exceeded");
    assertTrue(isActionNeededSequence3, "Action should be needed if limit is exceeded");
  }

  @Test
  void shouldTriggerActionWhenThereIsNoneDirtyResourceInSequence() {
    // given
    DirtySequenceStateManager manager = new DirtySequenceStateManager(2L);

    // when
    // sequence 1
    manager.newDirtyResource();
    boolean isActionNeededSequence1 = manager.checkIfActionIsNeededForNewSequence();
    // sequence 2
    boolean isActionNeededSequence2 = manager.checkIfActionIsNeededForNewSequence();

    // then
    assertFalse(isActionNeededSequence1, "Action should be not needed if limit is not exceeded");
    assertTrue(
        isActionNeededSequence2,
        "Action should be needed if there is sequence without dirty resources"
    );
  }

  private void assertTrue(boolean condition, String messageOnAssertionFailure) {
    assertThat(condition).as(messageOnAssertionFailure).isTrue();
  }

  private void assertFalse(boolean condition, String messageOnAssertionFailure) {
    assertThat(condition).as(messageOnAssertionFailure).isFalse();
  }
}
