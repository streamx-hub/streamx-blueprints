# Resource Downloader Processing Service

The `Resource Downloader Processing Service` is responsible for downloading web resources from specified URLs
and publishing the results to an outgoing resources channel.

## How it works

1. **Triggering a download**  
Downloads are initiated by publishing a `DownloadRequest` `CloudEvent` to the service’s `download-requests` incoming channel.


2. **Determining resource type**  
Upon receiving a request, the service performs an HTTP `GET` request to the provided URL. 
It inspects the `Content-Type` header of the response to determine the type of the downloaded resource.


3. **Publishing the downloaded resource**  
Based on the detected `Content-Type`, the service selects the appropriate `CloudEvent` type and publishes the resource to the output channel.

## Configuring payload types
The `DownloadRequest` event must specify which payload types to use when publishing different kinds of resources.
These should be set using the following fields:

- `pagePublishPayloadType` – for full HTML pages
- `webResourcePublishPayloadType` – for general web resources (e.g., scripts, stylesheets)
- `assetPublishPayloadType` – for assets such as images, fonts, or binary files

It is the caller’s responsibility to provide the correct payload type values in these fields.

**Note**: The service does not provide any feedback regarding the success or failure of downloading or publishing operations.

## Additional optimizations
- To optimize network usage and avoid unnecessary processing, the service checks the `Last-Modified` HTTP header (if available) before downloading a resource.
If the resource has not changed since the last successful download, the request is skipped, and nothing is published.

- For binary resources (assets), if the HTTP response includes a `Content-Encoding: gzip` header,
the service automatically decompresses the response body before publishing it.

## Sample usage (relevant code snippets only):
```java
  @Channel(Channels.DOWNLOAD_REQUESTS)
  Emitter<DownloadRequest> downloadRequestEmitter;

  DownloadRequest downloadRequest = new DownloadRequest(
    url, 
    publishKey,
    "page.payload.type",
    "web-resource.payload.type",
    "asset.payload.type"
  );

  CloudEvent event = CloudEventUtils.builderWithJsonData(downloadRequest)
    .withSubject(publishKey)
    .withType("DownloadRequest.TYPE_PUBLISHED")
    .build();

  downloadRequestEmitter.send(event);
```

## Configuration

- `streamx.blueprints.resource-downloader-processing-service.head-timeout-milliseconds`  
Defines the timeout (in milliseconds) for the HTTP `HEAD` request used to inspect the headers of a resource at the given URL.  
**Default**: 1500 milliseconds


- `streamx.blueprints.resource-downloader-processing-service.download-timeout-milliseconds`  
Defines the timeout (in milliseconds) for the HTTP GET request used to download the resource.  
**Default**: 5000 milliseconds


### Channels

Incoming channels:

- `download-requests`

Outgoing channels:

- `downloaded-resources`