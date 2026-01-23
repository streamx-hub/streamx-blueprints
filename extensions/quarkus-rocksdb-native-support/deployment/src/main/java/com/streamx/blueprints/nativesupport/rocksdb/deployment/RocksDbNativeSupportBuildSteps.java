package com.streamx.blueprints.nativesupport.rocksdb.deployment;

import com.streamx.blueprints.nativesupport.rocksdb.runtime.RocksDbNativeSupportFeature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;

@BuildSteps(onlyIf = NativeOrNativeSourcesBuild.class)
class RocksDbNativeSupportBuildSteps {

  @BuildStep
  FeatureBuildItem feature() {
    return new FeatureBuildItem("quarkus-rocksdb-native-support");
  }

  @BuildStep
  void build(BuildProducer<JniRuntimeAccessBuildItem> jniRuntimeAccessibleClasses,
      BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClasses) {

    jniRuntimeAccessibleClasses.produce(
        new JniRuntimeAccessBuildItem(true, true, true, RocksDBException.class, Status.class));

    runtimeInitializedClasses.produce(
        new RuntimeInitializedClassBuildItem(RocksDB.class.getName()));
  }

  @BuildStep
  NativeImageFeatureBuildItem rocksDbNativeSupportFeature() {
    return new NativeImageFeatureBuildItem(RocksDbNativeSupportFeature.class.getName());
  }
}