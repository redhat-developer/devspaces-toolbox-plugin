/*
 * Copyright (c) 2026 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.devtools.toolbox.buildlogic

import java.nio.file.Path
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

class DevToolboxPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    val skipRestart = readFlag(target, "skipToolboxRestart")

    val installTask = target.tasks.named("installPlugin")

    val quitTask = target.tasks.register("quitToolbox", QuitToolboxTask::class.java) {
      group = "toolbox"
      description = "Requests the running JetBrains Toolbox application to quit and waits for it to exit"
      restartDelayMs.set(readDelay(target))
      skip.set(skipRestart)
      onlyIf { !skip.get() }
      mustRunAfter("installPlugin")
    }

    val launchTask = target.tasks.register("launchToolbox", LaunchToolboxTask::class.java) {
      group = "toolbox"
      description = "Launches the JetBrains Toolbox application"
      skip.set(skipRestart)
      if (!skipRestart) {
        executable.set(ToolboxApp.resolveExecutable(target).toString())
      }
      onlyIf { !skip.get() }
      mustRunAfter(quitTask)
    }

    target.tasks.register("devReload", DevReloadTask::class.java) {
      group = "toolbox"
      description = "Builds and installs the plugin, restarts JetBrains Toolbox, and applies the change"
      dependsOn(installTask, quitTask, launchTask)
    }
  }

  private fun readFlag(target: Project, name: String): Boolean =
    (target.findProperty(name) as? String)?.toBoolean() == true

  private fun readDelay(target: Project): Long =
    (target.findProperty("toolboxRestartDelayMs") as? String)?.toLongOrNull() ?: 2000L

  @DisableCachingByDefault(because = "Controls the running Toolbox application")
  abstract class QuitToolboxTask : DefaultTask() {
    @get:Internal
    abstract val restartDelayMs: Property<Long>
    @get:Internal
    abstract val skip: Property<Boolean>

    @TaskAction
    fun quit() {
      println("Requesting JetBrains Toolbox to quit...")
      ToolboxApp.quit()
      println("Waiting ${restartDelayMs.get()}ms for JetBrains Toolbox to exit...")
      val exited = ToolboxApp.waitForExit(restartDelayMs.get())
      if (exited) {
        println("JetBrains Toolbox quit.")
      } else {
        throw GradleException(
          "JetBrains Toolbox did not exit within ${restartDelayMs.get()}ms of the quit request. " +
            "The quit request did not finish; launch was not performed. " +
            "Increase toolboxRestartDelayMs or quit Toolbox manually and re-run."
        )
      }
    }
  }

  @DisableCachingByDefault(because = "Launches the Toolbox application")
  abstract class LaunchToolboxTask : DefaultTask() {
    @get:Internal
    abstract val skip: Property<Boolean>
    @get:Internal
    abstract val executable: Property<String>

    @TaskAction
    fun launch() {
      val executable = this.executable.get()
      println("Launching JetBrains Toolbox: $executable")
      ToolboxApp.launch(Path.of(executable))
      println("JetBrains Toolbox launched.")
    }
  }

  @DisableCachingByDefault(because = "Orchestrates a Toolbox restart")
  abstract class DevReloadTask : DefaultTask() {
    @TaskAction
    fun devReload() {
      println("Dev reload complete: plugin installed and JetBrains Toolbox restarted.")
    }
  }
}
