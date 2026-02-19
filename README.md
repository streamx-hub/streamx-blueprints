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

## Cloud Event subjects

Blueprint services utilize `@Incoming` functions to process Cloud Events.
For these events, the `subject` field must be **non-null**.

### Namespacing logic

Cloud Event subjects support optional namespacing using the colon (`:`) character:

* **Format:** `namespace:actual-subject`
* **Parsing:** Everything before the first colon is treated as the **namespace**; the remainder is the **actual subject**.
* **Usage:** While services may use namespaces for internal logic, providing one is optional.  
  However, consistency in using the same approach for a single subject is required.
  Sending events with subjects `subject` and `:subject` may cause issues in StreamX Service Mesh monotonic event-time filtering
  and other core functionalities that rely on identifying events by subject.
  The namespace prefix is used only in Blueprint Services.

### Handling unparsed subject (the colon prefix)

If you need to use the entire subject without any splitting, **prefix the subject with a colon (`:`)**.
This forces the Blueprints to treat the namespace as empty and the rest of the string as the full subject.

#### Examples:
| Subject Input        | Namespace | Resolved Subject |
|----------------------|-----------|------------------|
| `orders:12345`       | `orders`  | `12345`          |
| `finance:invoice:99` | `finance` | `invoice:99`     |
| `:id-123`            | *(empty)* | `id-123`         |
| `id-123`             | *(empty)* | `id-123`         |


## Code coverage tips
 - Don't use `io.quarkus.logging.Log` in main code, since it causes the whole class using that logger to have 0% jacoco coverage