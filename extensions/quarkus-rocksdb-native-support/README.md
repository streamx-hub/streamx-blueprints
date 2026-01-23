# Quarkus RocksDB Native Support Extension

This Quarkus extension provides seamless support for **RocksDB** in native executables.

It automates the complex JNI (Java Native Interface) registration and ensures the correct architecture-specific RocksDB native libraries are bundled into the final binary.

## Project Structure

The project consists of two main modules:

 - `deployment`: Contains the build-time logic. It instructs GraalVM on which classes need JNI access and ensures the native library is initialized at runtime rather than build-time.

 - `runtime`: Contains a GraalVM feature that dynamically identifies the correct JNI library for the target platform and includes it in the native image resources.

## How to Use

### 1. Add the Dependency

Add the following dependency to your Quarkus application's pom.xml:
```xml
<dependency>
  <groupId>com.streamx.blueprints</groupId>
  <artifactId>quarkus-rocksdb-native-support</artifactId>
  <version>${project.version}</version>
</dependency>
````

### 2. Build for Native
Since the extension is gated by the NativeBuild condition, it stays out of your way during standard JVM development mode but activates automatically during a native build:
```bash
./mvnw clean install -Dnative
```
