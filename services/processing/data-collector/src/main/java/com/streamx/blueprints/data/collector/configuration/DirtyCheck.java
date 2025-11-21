package com.streamx.blueprints.data.collector.configuration;

public interface DirtyCheck {

  Long maxDirtySequenceCount();

  String interval();

  String delay();
}
