# OpenShift Dev Spaces (Eclipse Che) plugin for JetBrains Toolbox
Plugin for JetBrains Toolbox enables local desktop development experience with the IntelliJ IDEs connected to Red Hat OpenShift Dev Spaces.

<img width="486" height="746" alt="image" src="https://github.com/user-attachments/assets/84e1983c-2508-4318-a7a6-dc0d4e11d787" />

## How to use

### 1. Install Toolbox
Install the JetBrains Toolbox App from https://www.jetbrains.com/toolbox-app/

### 2. Install Red Hat OpenShift Dev Spaces plugin for Toolbox
a. Clone this repository: `git clone git@github.com:redhat-developer/devspaces-toolbox-plugin.git`

b. Run the following command to build the plugin and install it to the Toolbox App: `./gradlew installPlugin` 

c. Restart the Toolbox App if it's running.

### 3. Connect to a workspace
a. Start a workspace by selecting `JetBrains IDE (over Toolbox)` editor option on the Dashboard.
Alternatively, you can provide [this custom editor definition](https://gist.githubusercontent.com/azatsarynnyy/56a07b64fbd13fac38844baee005971f/raw/78bfac4d93673438c9faff05829eaa2869c0600d/editor-over-ssh) on the Dashboard.

b. Once a workspace is up and running, follow the provided instructions to open the Toolbox App.

c. Once the Toolbox App is connected to a remote, click it and choose an IDE to install in the CDE.

d. Once a chosen IDE is installed, go to the previous page and click the project folder. Local ThinClient will connect to CDE.


## Release
- Find a draft release on the [Releases](https://github.com/redhat-developer/devspaces-toolbox-plugin/releases) page. The draft is created and updated automatically on each push to the `main` branch.
- Edit the draft:
  - Click the `Generate release notes` button and edit the release notes if needed
  - Click the `Publish release` button. The [Release](https://github.com/redhat-developer/devspaces-toolbox-plugin/blob/main/.github/workflows/release.yml) Workflow will attach the built plugin artifact to the published release and upload the plugin artifact to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31372-red-hat-openshift-dev-spaces).
- Bump the `version` in the [gradle.properties](https://github.com/redhat-developer/devspaces-toolbox-plugin/blob/main/plugin/build.gradle.kts) file.
