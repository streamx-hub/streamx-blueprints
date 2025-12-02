package com.streamx.blueprints.opensearch.sink.config;

import com.senacor.elasticsearch.evolution.core.internal.migration.execution.HistoryRepositoryImpl;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This configuration is for running the service in Quarkus Native mode.
 * It lists the classes that need their reflection data retained at runtime.
 *
 * <p>The {@code HistoryRepositoryImpl} class contains nested classes that rely on
 * Jackson annotations for JSON serialization. In a GraalVM native image, this
 * metadata would be removed unless the classes are explicitly registered for
 * reflection. This configuration ensures that Jackson can properly serialize
 * and deserialize these types at runtime.</p>
 */
@RegisterForReflection(targets = {
    HistoryRepositoryImpl.class
})
public class RegisterForReflectionConfiguration {

}
