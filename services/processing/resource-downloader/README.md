# Resource Downloader

The `Resource Downloader` service is responsible for downloading web resources from specified URLs
and emitting the results to an outgoing resources channel.

## How it works

1. **Triggering a download**  
Downloads are initiated by sending a `DownloadRequest` `CloudEvent` to the service’s `download-requests` incoming channel.


2. **Determining resource type**  
Upon receiving a request, the service performs an HTTP `GET` request to the provided URL. 
It inspects the `Content-Type` header of the response to determine the type of the downloaded resource.


3. **Emitting the downloaded resource**  
Based on the detected `Content-Type`, the service selects the appropriate `CloudEvent` type and emits the resource to the output channel.

## Configuring payload types
The `DownloadRequest` event must specify which payload types to use when emitting different kinds of resources.
These should be set using the following fields:

- `emittedPageType` – for full HTML pages
- `emittedWebResourceType` – for general web resources (e.g., scripts, stylesheets)
- `emittedAssetType` – for assets such as images, fonts, or binary files

It is the caller’s responsibility to provide the correct payload type values in these fields.

**Note**: The service does not provide any feedback regarding the success or failure of downloading or emitting operations.

## Additional optimizations
- To optimize network usage and avoid unnecessary processing, the service checks the `Last-Modified` HTTP header (if available) before downloading a resource.
If the resource has not changed since the last successful download, the request is skipped, and nothing is emitted.

- For binary resources (assets), if the HTTP response includes a `Content-Encoding: gzip` header,
the service automatically decompresses the response body before emitting it.

## Sample usage (relevant code snippets only):
```java
  @Channel(Channels.DOWNLOAD_REQUESTS)
  Emitter<DownloadRequest> downloadRequestEmitter;

  DownloadRequest downloadRequest = new DownloadRequest(
    url, 
    emitKey,
    "page.payload.type",
    "web-resource.payload.type",
    "asset.payload.type"
  );

  CloudEvent event = CloudEventUtils.builderWithJsonData(downloadRequest)
    .withSubject(emitKey)
    .withType(DownloadRequest.DOWNLOAD_EVENT_TYPE)
    .build();

  downloadRequestEmitter.send(event);
```

## Repeatable Downloads  

The service supports **Repeatable Downloads**, allowing you to schedule a specific URL to be downloaded at regular intervals.

This is useful for resources that might change or get frequently updated by other services.
Such resources can be registered for repeatable download.
Every configurable interval period, the service will check if the
resource has changed, and download it again to the storage if so.

### Scheduling a Repeatable Download
To schedule a recurring download, send a `DownloadRequest` `CloudEvent` with the following type:

`DownloadRequest.REPEATABLE_DOWNLOAD_EVENT_TYPE`

#### Behavior:  
 - **Immediate Action**: The resource is downloaded immediately upon registration.
 - **Scheduling**: The URL is added to the internal scheduler and will be re-downloaded periodically based on your configuration.

### Cancelling a Repeatable Download
To stop a recurring download, send a `DownloadRequest` `CloudEvent` with the following type:

`DownloadRequest.STOP_REPEATABLE_DOWNLOAD_EVENT_TYPE`

#### Behavior:  
 - **Unregistration**: The URL is removed from the periodic download collection.
 - **Finalization**: Future scheduled downloads for this URL will cease.

#### Race Condition Note:  
Because repeatable downloads are executed in a separate background thread,
a cancellation request might arrive while a download is already in progress.
In this scenario, one final download may occur before the cancellation takes effect.

## Configuration

- `streamx.blueprints.resource-downloader.head-timeout-millis`  
  Defines the timeout (in milliseconds) for the HTTP `HEAD` request used to inspect the headers of a
  resource at the given URL.
  **Default**: 1500 milliseconds


- `streamx.blueprints.resource-downloader.download-timeout-millis`  
  Defines the timeout (in milliseconds) for the HTTP GET request used to download the resource.  
  **Default**: 5000 milliseconds


- `streamx.blueprints.resource-downloader.repeatable-url-pattern`  
  An optional Regular Expression used to automatically promote standard download requests to "repeatable" status based on their URL.
  If an incoming `DownloadRequest` has a URL that matches this pattern, the service will treat it as a repeatable request
  even if the event type is the standard `DownloadRequest.DOWNLOAD_EVENT_TYPE`.
  This allows you to enforce global policies for specific domains or file types without requiring the event sender to change the `CloudEvent` type.


- `streamx.blueprints.resource-downloader.repeat-interval-millis`  
  Defines the interval (in milliseconds) between scheduled download attempts for repeatable resources.
  A new version of the resource is only downloaded if the remote content has changed since the last successful download.  
  **Default**: 30000 milliseconds


### Channels

Incoming channels:

- `download-requests`

Outgoing channels:

- `downloaded-resources`