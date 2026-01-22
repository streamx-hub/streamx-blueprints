package com.streamx.blueprints.nativesupport.rocksdb.runtime;

import io.quarkus.runtime.annotations.Recorder;
import org.jboss.logging.Logger;
import org.rocksdb.RocksDB;

@Recorder
public class RocksDbRecorder {

  private static final Logger log = Logger.getLogger(RocksDbRecorder.class);

  public void loadRocksDb() {
    log.info("Loading RocksDB library");
    RocksDB.loadLibrary();
  }
}
