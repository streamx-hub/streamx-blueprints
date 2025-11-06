## Before you contribute

To contribute, use GitHub Pull Requests, from your **own** branch.

Also, make sure you have set up your Git authorship correctly:

```
git config --global user.name "Your Full Name"
git config --global user.email name.lastname@ds.pl
```

If you use different computers to contribute, please make sure the name is the same on all your
computers.

We use this information to acknowledge your contributions in release announcements.

## Branch Naming

All changes in the project should be made on specific branches following the Git Flow model. Please
create a new branch with the following pattern:

>{branch type}/{Jira issue key}{description(optional)}

For example:

- `feature/ISSUE-123-add-the-new-feature`: If you are working on a new feature,
- `bugfix/ISSUE-123-fix-the-bug`: If you are working on a bug fix,
- `hotfix/ISSUE-123-fix-the-critical-bug`: If you are working on critical hotfixes,
- `release/version`: If you are releasing a version.

## Commits

Each commit should be reviewed for project compatibility and contain logically cohesive changes.
Please use squash commits to maintain a clear repository history.

## Commit Messages

Each commit should have a short but clear description of the changes made. Always include the
Jira issue key you are working on in the commit message.

## Pull Requests

Each pull request (PR) title should start with Jira issue key followed by a description of the changes.
Begin the description with a keyword that specifies the type of change provided, such as: 
>add, fix, improve, refactor, bump, clean, remove, or similar.

Please create a PR title with the following pattern:
>{[Jira issue key]}{Add/Improve/Fix/Remove/Bump - description of the changes}

Example PR titles:

- [ISSUE-123] Add a new fancy feature
- [ISSUE-123] Improve an existing feature
- [ISSUE-123] Fix a defect

Follow the [PR template](.github/pull_request_template.md) to fulfill all sufficient
information related to the changes.

The PR author is responsible for driving the approval process by addressing reviewer feedback,
incorporating necessary changes, and keeping the discussion active.

## Change Acceptance

Every change must be reviewed by a team member and approved by the relevant individuals, 
who are recognized as code owners, before merging into the main branch. 
Please exercise patience during the review process.

Once the change is approved, the author of the change is responsible for performing the merge.
Squash and merge is the only allowed method for merging.

## Branch management

To maintain the repository clean and to minimize conflicts, feature/bugfix/hotfix branches
will be automatically deleted when a change is finished and merged.

## Coding convention

[Guideline for developers](./GUIDELINES.md)

### Code formatting

The project use [Google Style Guides](https://github.com/google/styleguide) forced by Maven
Checkstyle plugin. Configure code formatter by importing the file `codestyle/intellij-java-google-style.xml`
from the project root directory.
It's a copy of Google formatter from
their [GitHub repository](https://github.com/google/styleguide/blob/gh-pages/intellij-java-google-style.xml).

### Maven dependencies management

- The dependencies scope and version should be done in the dependency management section.
- Versions should be set via properties.
- If dependency is specific for the module, it can be added only at the module level.

### Tests

- At the moment of active changes in some are implement just happy paths tests.
- When solution design is stable test also:
    - corner cases
    - wrong formats handling (like wrong message format)
    - exceptions handling
- Always use the lowest possible tests level for tests, for example do not test corner cases use at
  integration tests (IT) level
- While implementing tests with Smallrye Reactive Messaging make sure that test checks if original
  Smallrye functionality works.

### GitHub actions

See this guideline: https://teamds.atlassian.net/wiki/spaces/WEBS/pages/603619329/GitHub+Actions
