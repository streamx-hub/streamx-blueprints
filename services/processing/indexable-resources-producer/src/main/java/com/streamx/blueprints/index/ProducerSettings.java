package com.streamx.blueprints.index;

import com.streamx.blueprints.data.WebResource;

public record ProducerSettings<T extends WebResource>(
    Class<T> incomingType,
    String incomingPublishedEventType,
    String incomingUnpublishedEventType,
    String outgoingPublishedEventType,
    String outgoingUnpublishedEventType) {

}
