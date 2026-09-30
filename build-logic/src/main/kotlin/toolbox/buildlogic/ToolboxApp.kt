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

import org.gradle.api.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * Cross-platform helpers for locating and controlling a local JetBrains Toolbox installation.
 */
object ToolboxApp {

    /**
     * Detects the operating system the current process is running on,
     * based on the `os.name` system property.
     */
    private class OperatingSystem(osName: String = System.getProperty("os.name").orEmpty()) {

        private enum class Type { WINDOWS, LINUX, MAC, UNKNOWN }

        private val type: Type = when {
            osName.contains("win", ignoreCase = true) -> Type.WINDOWS
            osName.contains("nix", ignoreCase = true) ||
                    osName.contains("nux", ignoreCase = true) ||
                    osName.contains("aix", ignoreCase = true) -> Type.LINUX
            osName.contains("mac", ignoreCase = true) ||
                    osName.contains("darwin", ignoreCase = true) -> Type.MAC
            else -> Type.UNKNOWN
        }

        val isWindows: Boolean get() = type == Type.WINDOWS
        val isLinux: Boolean get() = type == Type.LINUX
        val isMac: Boolean get() = type == Type.MAC
    }

    private val os = OperatingSystem()

    private fun userHome(): Path = Path.of(System.getProperty("user.home"))

  /**
   * Directory where Toolbox stores installed plugins:
   * - Windows: %LOCALAPPDATA%/JetBrains/Toolbox/cache/plugins
   * - Linux: $XDG_CACHE_HOME (or ~/.cache)/JetBrains/Toolbox/plugins
   * - macOS: ~/Library/Caches/JetBrains/Toolbox/plugins
   */
  fun pluginsDir(): Path {
    val toolboxDir = when {
      os.isWindows -> (System.getenv("LOCALAPPDATA")?.let { Path.of(it) } ?: (userHome() / "AppData" / "Local")) / "JetBrains" / "Toolbox"
      os.isLinux -> (System.getenv("XDG_CACHE_HOME")?.let { Path.of(it) } ?: (userHome() / ".cache")) / "JetBrains" / "Toolbox"
      os.isMac -> userHome() / "Library" / "Caches" / "JetBrains" / "Toolbox"
      else -> error("Unknown os: ${System.getProperty("os.name")}")
    }
    return when {
      os.isWindows -> toolboxDir / "cache"
      else -> toolboxDir
    } / "plugins"
  }

  /**
   * Resolves the path to the Toolbox executable using the following fallback chain:
   * 1. `-PtoolboxExecutable` Gradle property
   * 2. `which`/`where` on PATH
   * 3. Well-known default installation locations
   */
  fun resolveExecutable(project: Project): Path {
    val fromProperty = project.properties["toolboxExecutable"] as? String
    if (!fromProperty.isNullOrBlank()) {
      val executable = Path.of(fromProperty)
      if (!(executable.exists() && Files.isExecutable(executable))) {
        error(
          "-PtoolboxExecutable points to '$fromProperty', which does not exist or is not executable. " +
            "Provide a valid path via -PtoolboxExecutable=/path/to/jetbrains-toolbox"
        )
      }
      return executable
    }

    val pathCandidates = when {
      os.isWindows -> listOf("JetBrains Toolbox.exe", "jetbrains-toolbox")
      else -> listOf("jetbrains-toolbox")
    }
    for (candidate in pathCandidates) {
      which(candidate)?.let { return it }
    }

    val localAppData = System.getenv("LOCALAPPDATA")?.let { Path.of(it) } ?: (userHome() / "AppData" / "Local")
    val defaults = when {
      os.isMac -> listOf(
        Path.of("/Applications/JetBrains Toolbox.app/Contents/MacOS/jetbrains-toolbox")
      )
      os.isWindows -> listOf(
        localAppData / "Programs" / "JetBrains Toolbox" / "JetBrains Toolbox.exe",
        localAppData / "JetBrains" / "Toolbox" / "bin" / "jetbrains-toolbox.exe"
      )
      os.isLinux -> listOf(
        System.getenv("TOOLBOX_HOME")?.let { Path.of(it) / "bin" / "jetbrains-toolbox" },
        userHome() / ".local" / "share" / "JetBrains" / "Toolbox" / "bin" / "jetbrains-toolbox"
      )
      else -> emptyList()
    }.filterNotNull()

    defaults.firstOrNull { it.exists() }?.let { return it }

    error(
      "Could not find JetBrains Toolbox executable. " +
        "Please install Toolbox or provide its path via -PtoolboxExecutable=/path/to/jetbrains-toolbox"
    )
  }

  /**
   * Requests the running Toolbox application to quit, if any.
   */
  fun quit() {
    if (os.isWindows) {
      for (imageName in taskListImageNames) {
        try {
          ProcessBuilder("taskkill", "/IM", imageName).start().waitFor()
        } catch (_: Exception) {
          // Ignore
        }
      }
      Thread.sleep(750)
      for (imageName in taskListImageNames) {
        try {
          ProcessBuilder("taskkill", "/F", "/IM", imageName).start().waitFor()
        } catch (_: Exception) {
          // Ignore
        }
      }
      return
    }
    val command = when {
      os.isMac -> listOf("osascript", "-e", "quit app \"JetBrains Toolbox\"")
      os.isLinux -> listOf("pkill", "-f", "[/]jetbrains-toolbox")
      else -> return
    }
    try {
      ProcessBuilder(command).start().waitFor()
    } catch (_: Exception) {
      // Not running or no permission – ignore
    }
    if (os.isLinux) {
      try {
        ProcessBuilder("pkill", "-x", "jetbrains-toolb").start().waitFor()
      } catch (_: Exception) {
        // Ignore
      }
    }
  }

  /**
   * Starts the Toolbox application using the given executable.
   *
   * Requires that no Toolbox is running before launch: if [isRunning] reports
   * a running instance, a previous quit did not finish and launching is
   * refused with an [IllegalStateException].
   *
   * Some launchers (especially on macOS) spawn the real application and
   * exit immediately. The launch is therefore only treated as a failure
   * when the started process died AND no Toolbox process appears
   * within a short poll window afterwards (e.g. bad executable path or an
   * early crash).
   */
  fun launch(executable: Path) {
    if (isRunning()) {
      throw IllegalStateException(
        "JetBrains Toolbox is already running; quit did not finish. " +
          "Close or force-quit the running instance before launching $executable."
      )
    }
    val process = ProcessBuilder(executable.toString()).start()
    Thread.sleep(400)
    if (process.isAlive) {
      return
    }
    if (waitForHandoff()) {
      // Launcher handed off to the real Toolbox process – treat as success
      return
    }
    val exitValue = try {
      process.waitFor()
    } catch (_: InterruptedException) {
      -1
    }
    throw IllegalStateException(
      "JetBrains Toolbox process exited immediately (exit code $exitValue) after launch: $executable. " +
        "Check that the executable path is valid and can start on this system."
    )
  }

   /**
    * Waits for the Toolbox application to fully exit after a quit request.
    *
    * Polls until the process is no longer running or [delayMs] elapses.
    * Returns `true` when the process is gone (or was never running),
    * `false` when it is still running after the timeout.
    */
   fun waitForExit(delayMs: Long = 2000): Boolean {
     val deadline = System.currentTimeMillis() + delayMs
     while (System.currentTimeMillis() < deadline) {
       if (!isRunning()) return true
       Thread.sleep(pollIntervalMs)
     }
     return !isRunning()
   }

  private val pollIntervalMs = 250L

  private val taskListImageNames = listOf("JetBrains Toolbox.exe", "jetbrains-toolbox.exe")

    /**
     * Used after [launch] has started a process that already exited.
     *
     * Because [launch] guarantees no Toolbox was running before the start,
     * this polls [isRunning] for up to [timeoutMs] at [pollIntervalMs]
     * intervals, returning `true` when a *new* Toolbox instance appears
     * (i.e. the launcher handed off to the real application).
     */
   private fun waitForHandoff(timeoutMs: Long = 2000): Boolean {
     val deadline = System.currentTimeMillis() + timeoutMs
     while (System.currentTimeMillis() < deadline) {
       if (isRunning()) return true
       Thread.sleep(pollIntervalMs)
     }
     return isRunning()
   }

   /**
    * Whether a Toolbox process is currently running on this machine.
    */
   private fun isRunning(): Boolean = when {
    os.isWindows -> taskListImageNames.any { taskListShows(it) }
    os.isMac -> exitsSuccessfully("pgrep", "-x", "jetbrains-toolbox")
    os.isLinux -> exitsSuccessfully("pgrep", "-f", "[/]jetbrains-toolbox") ||
            exitsSuccessfully("pgrep", "-x", "jetbrains-toolb")
    else -> exitsSuccessfully("pgrep", "-x", "jetbrains-toolbox")
  }

  /**
   * Runs `tasklist /FI "IMAGENAME eq <imageName>"` and reports whether
   * the image name appears in the output (tasklist always exits 0).
   */
  private fun taskListShows(imageName: String): Boolean {
    val output = runCatching {
      val process = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq $imageName").start()
      val text = process.inputStream.bufferedReader().use { it.readText() }
      process.waitFor()
      text
    }.getOrNull() ?: return false
    return output.contains(imageName, ignoreCase = true)
  }

  /**
   * Runs the given command and returns true when it exits with status 0
   * (e.g. `pgrep` finds a matching process).
   */
  private fun exitsSuccessfully(vararg command: String): Boolean = try {
    val process = ProcessBuilder(*command).start()
    process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor() == 0
  } catch (_: Exception) {
    false
  }

  private fun which(name: String): Path? {
    val lookup = when {
      os.isWindows -> "where"
      else -> "which"
    }
    return try {
      val process = ProcessBuilder(lookup, name).start()
      val output = process.inputStream.bufferedReader().readText().trim()
      process.waitFor()
      output.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf { it.exists() && Files.isExecutable(it) }
    } catch (_: Exception) {
      null
    }
  }
}
