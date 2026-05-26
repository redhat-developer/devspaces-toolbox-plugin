# Red Hat OpenShift Dev Spaces (Eclipse Che) plugin for JetBrains Toolbox

[![Build](https://github.com/redhat-developer/devspaces-toolbox-plugin/workflows/Build/badge.svg)](https://github.com/redhat-developer/devspaces-toolbox-plugin/actions/workflows/build.yml)

Plugin for JetBrains Toolbox enables local desktop development experience with the IntelliJ IDEs connected to Red Hat OpenShift Dev Spaces.

<img width="486" height="746" alt="image" src="https://github.com/user-attachments/assets/84e1983c-2508-4318-a7a6-dc0d4e11d787" />

## Developing
Edit-run loop:
1. Run `./gradlew installPlugin` to build the plugin and install it directly to Toolbox.
2. Restart the Toolbox to apply the installed plugin.

To check the logs:
1. Go to the `About` page.
2. Click `Show log files`.
3. Open `toolbox.latest.log`.

## Release
- Find a draft release on the [Releases](https://github.com/redhat-developer/devspaces-toolbox-plugin/releases) page. The draft is created and updated automatically on each push to the `main` branch.
- Edit the draft:
  - Click the `Generate release notes` button and edit the release notes if needed
  - Click the `Publish release` button. The [Release](https://github.com/redhat-developer/devspaces-toolbox-plugin/blob/main/.github/workflows/release.yml) Workflow will attach the built plugin artifact to the published release and upload the plugin artifact to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31372-red-hat-openshift-dev-spaces).
- Bump the `version` in the [gradle.properties](https://github.com/redhat-developer/devspaces-toolbox-plugin/blob/main/plugin/build.gradle.kts) file.
