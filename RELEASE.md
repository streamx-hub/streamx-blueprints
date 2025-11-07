# Release process

## Updating dependencies

Before releasing `StreamX Blueprints` ensure that all required pull requests are merged, and the latest releases of `StreamX` are used.
It may be required to perform `StreamX` release prior to `StreamX Blueprints` release, if there are unreleased changes.

## Release automation

The Release process is partially automated. To perform a release of blueprints, follow steps:

1. Go to [GH Actions](https://github.com/streamx-com/streamx-blueprints/actions)
2. Run [Release: Maven and Docker Artifacts](https://github.com/streamx-com/streamx-blueprints/actions/workflows/release-maven-and-docker-artifacts.yaml)
   action on main branch.
3. Create new [GH Release](https://github.com/streamx-com/streamx-blueprints/releases/new) based on
   tag created in step 2. Release notes should be generated using "Generate release notes" button.

## Released artifacts visibility

After a successful new blueprint release, its visibility has to be changed manually in GH artifacts.

1. Go to: [Packages](https://github.com/orgs/streamx-com/packages?repo_name=streamx-blueprints)
2. Find your container image package and change its visibility to Public in settings.
