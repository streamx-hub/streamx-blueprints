# Mesh

This folder contains the data needed to run the mesh. It contains the following:
* [configs](./configs) - contains the mesh configuration that can be shared
* [secrets](./secrets) - contains the mesh configuration that cannot be shared because it contains sensitive data
* [mesh.yaml](./mesh.yaml) - configures mesh services using values located in `./secrets` and `./configs`

Full mesh reference is available on https://www.streamx.dev/guides/streamx-mesh-yaml-reference.html.
