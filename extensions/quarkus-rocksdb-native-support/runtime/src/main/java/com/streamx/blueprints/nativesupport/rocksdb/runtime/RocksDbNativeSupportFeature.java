package com.streamx.blueprints.nativesupport.rocksdb.runtime;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.jboss.logging.Logger;
import org.rocksdb.util.Environment;

public class RocksDbNativeSupportFeature implements Feature {

  private static final Logger log = Logger.getLogger(RocksDbNativeSupportFeature.class);

  @Override
  public void afterRegistration(AfterRegistrationAccess access) {
    String libraryFileName = Environment.getJniLibraryFileName("rocksdb");
    log.infof("Adding resource: %s", libraryFileName);
    RuntimeResourceAccess.addResource(Environment.class.getModule(), libraryFileName);
  }

}