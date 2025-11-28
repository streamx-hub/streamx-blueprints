package com.streamx.blueprints.image.generator.configuration;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This configuration is for running the service in Quarkus Native mode.
 * It lists the classes that need their reflection data retained at runtime.
 *  - com.sksamuel.scrimage image optimization library needs to instantiate the
 *    two below classes at runtime
 */
@RegisterForReflection(classNames = {
    "com.drew.metadata.exif.ExifIFD0Directory",
    "com.drew.metadata.exif.ExifSubIFDDirectory"
})
public class RegisterForReflectionConfiguration {

}
