package com.streamx.blueprints.index;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
record IndexableResourceContent(String title, String content) {

}
