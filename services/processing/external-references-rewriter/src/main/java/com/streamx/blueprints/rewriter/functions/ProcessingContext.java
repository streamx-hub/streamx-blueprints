package com.streamx.blueprints.rewriter.functions;

import com.streamx.blueprints.data.Resource;
import com.streamx.blueprints.rewriter.functions.settings.BaseProcessingSettings;

public record ProcessingContext(Resource payload, String payloadType,
                                String resourcePath, BaseProcessingSettings<?> settings) {
}
