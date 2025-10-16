## Blueprints development guidelines

* General guidelines
    * Do not use "blueprint" in service name
    * Do not use processing/delivery/edge service in image names
    * Service name should be short and describe service functionality - not the characteristics
    * Do not configure outgoing channels to "write back" to incoming channels
    * Topics should have short and meaningful names
    * Service should have one responsibility. Break down service in to smaller single responsibility services.
    * Services should not relay data. We have dedicated service for that. Only generated and modified data should be emitted from service.
    * Only delivery services should read from outbox topics.
    * It's recommended to place all channel names used by a service into a Channels class as constants, making it easier to review the service's scope at a glance.

* Incoming channels
    * If a service handles different types of incoming events in the same way, use a single incoming channel for all the events
    * If event types are processed differently, use separate incoming channels

* Outgoing channels
    * If a service emits different types of events, and all downstream services are expected to handle them the same way, use a single outgoing channel
    * If different types of outgoing events require different handling by downstream services, assign each event type its own outgoing channel
