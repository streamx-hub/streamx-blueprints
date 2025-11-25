# StreamX Blueprints Project

StreamX services implementations dedicated to be a showcase of how to develop services and also
production ready services which can be used to create StreamX mesh.

Full documentation is available on https://www.streamx.dev/guides/index.html.

## Project structure

Each StreamX project should be organized in a specific way and should follow the conventions described below.

The following are recommended directories:

* [mesh](./example-mesh/README.md) - resources required to configure and run StreamX Mesh
* [services](./services/README.md) - your StreamX Processing and Edge Services (as well as other Maven modules) should be placed here

## Prerequisites

To work with this repository you need:
* OpenJDK 21+ installed with JAVA_HOME configured appropriately
* Docker
* StreamX CLI

### StreamX CLI

To install StreamX CLI, run:
```sh
# for Linux/MacOS
brew install streamx-dev/tap/streamx
```
or
```shell
# for Windows
scoop bucket add streamx-dev https://github.com/streamx-dev/scoop-streamx-dev.git
scoop install streamx
```

To upgrade StreamX CLI, run:
```sh
# for Linux/MacOS
brew update
brew upgrade streamx
```
or
```shell
# for Windows
scoop update streamx
```

For more information, visit [StreamX CLI Reference](https://www.streamx.dev/guides/streamx-command-line-interface-reference.html#_installing_the_cli).

## Packaging

To build StreamX project, run:
```shell
# For Linux/MacOS
./mvnw clean install
```
or 
```shell
# For Windows
mvnw.cmd clean install
```

This command builds both the Maven artefacts and the Docker images needed to start the local StreamX mesh.

## Running local StreamX Mesh

To start local instance of Mesh run:

```shell
cd example-mesh
streamx run
```
The above command runs the StreamX mesh defined in the `mesh.yaml` file located in the current directory.
For more information, visit [StreamX CLI Reference](https://www.streamx.dev/guides/streamx-command-line-interface-reference.html#_streamx_run).

## Debugging

If you need to debug a single service as part of the StreamX Mesh, you need to comment out debugged service in your mesh YAML.  

Then, run your mesh using standard `streamx run` command:
```shell
cd example-mesh
streamx run
```

Finally make sure that the `application-streamx-mesh-debug.properties` file in the debugged service reflects the mesh configuration (refs configuration, exposed port, etc.). 
Then run the debugged service with the `streamx-mesh-debug` profile, e.g. for `rendering-engine` run the following command:
```shell
cd services/processing/rendering-engine
quarkus dev -Dquarkus.profile=streamx-mesh-debug
```

## Known issues to be resolved

Search by ticket URL to find references to the given issue in code.

1. Empty `Multi<Payload>` is not acked when the function produces an empty stream.
  https://github.com/smallrye/smallrye-reactive-messaging/issues/3232

What should work no matter if the result Multi is empty or not:
```java
@Incoming(Channels.INCOMING_CHANNEL)
@Outgoing(Channels.OUTGOING_CHANNEL)
@Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
public Multi<CloudEvent> consume(CloudEvent data) {
  ...
  return Multi.createFrom().items(myItemsStream);
}
```
Temporary workaround:
```java
   @Incoming(Channels.INCOMING_CHANNEL)
   @Outgoing(Channels.OUTGOING_CHANNEL)
   public Multi<Message<CloudEvent>> consume(Message<CloudEvent> dataMessage) {
     ...
     return Multi.createFrom().items(myItemsStream)
       .map(Message::of)
       .onCompletion()
       .call(() -> Uni.createFrom().completionStage(layoutMessage.ack()));
   }
```

## Code coverage tips
 - Don't use `io.quarkus.logging.Log` in main code, since it causes the whole class using that logger to have 0% jacoco coverage