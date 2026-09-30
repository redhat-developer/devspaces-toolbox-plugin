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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import kotlin.io.path.div

class InstallToolboxPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val installTask = target.tasks.register("installPlugin", InstallTask::class.java) {
      group = "toolbox"
      description = "Builds and installs the plugin into the local JetBrains Toolbox plugins directory"
      extensionId.set(target.group.toString())
      extensionJsonFile.set(target.layout.buildDirectory.file("generated/extension.json"))
      pluginRuntimeDependencies.from(target.configurations.named("runtimeClasspath"))
    }
    installTask.configure { dependsOn(target.tasks.named("assemble")) }
  }

  @DisableCachingByDefault(because = "Installs files into user directory")
  abstract class InstallTask : DefaultTask() {
    @get:Input
    abstract val extensionId: Property<String>

    @get:InputFile
    abstract val extensionJsonFile: RegularFileProperty

    @get:Classpath
    abstract val pluginRuntimeDependencies: ConfigurableFileCollection

    @TaskAction
    fun install() {
      println("Installing Toolbox plugin...")
      project.sync {
        val targetDir = ToolboxApp.pluginsDir() / extensionId.get()

        // Copy jar task output and the generated JSON
        from(project.tasks.getByName("jar"))
        from(extensionJsonFile)
        from(pluginRuntimeDependencies)

        // Copy selected resources
        from("src/main/resources") {
          include("dependencies.json")
          include("icon.svg")
          include("pluginIcon.svg")
        }
        into(targetDir)
      }
    }
  }
}
