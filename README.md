# OpenShift Dev Spaces (Eclipse Che) plugin for JetBrains Toolbox
Plugin for JetBrains Toolbox enables local desktop development experience with the IntelliJ IDEs connected to Red Hat OpenShift Dev Spaces.

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
