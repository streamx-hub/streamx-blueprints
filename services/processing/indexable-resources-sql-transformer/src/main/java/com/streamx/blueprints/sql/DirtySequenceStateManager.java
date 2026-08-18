package com.streamx.blueprints.sql;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.logging.Logger;

class DirtySequenceStateManager {

  private static final Logger log = Logger.getLogger(DirtySequenceStateManager.class);

  private final long maxDirtySequenceCount;
  private final AtomicBoolean dirty;
  private final AtomicLong previouslyDirtyCount;

  public DirtySequenceStateManager(long maxDirtySequenceCount) {
    this.maxDirtySequenceCount = maxDirtySequenceCount;
    dirty = new AtomicBoolean(false);
    previouslyDirtyCount = new AtomicLong(0);
  }

  void newDirtyResource() {
    dirty.set(true);
  }

  boolean checkIfActionIsNeededForNewSequence() {
    boolean dirty = this.dirty.getAndSet(false);
    long count = previouslyDirtyCount.get();

    if (dirty) {
      if (count < maxDirtySequenceCount) {
        log.debugf("Action has been done. "
            + "Waiting for other dirty resources to process for action. "
            + "DirtySequenceCount = %s", count);
        previouslyDirtyCount.incrementAndGet();
        return false;
      }
      log.debugf("Another action has been done. "
          + "However dirtySequenceThreshold was exceeded, so action will be executed");
      previouslyDirtyCount.set(0);
      return true;
    }

    if (count > 0) {
      log.debugf("There is no another dirty resources, so action "
          + "with previously resources will be triggered");
      previouslyDirtyCount.set(0);
      return true;
    }

    log.debugf("There is no another dirty resources and "
        + "no previously dirty resources are waiting to trigger action");
    return false;
  }
}
