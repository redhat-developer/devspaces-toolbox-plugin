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
package com.redhat.devtools.toolbox.environment

import com.jetbrains.toolbox.api.core.util.LoadableState
import com.jetbrains.toolbox.api.remoteDev.environments.CachedIdeStub
import com.jetbrains.toolbox.api.remoteDev.environments.CachedProject
import com.jetbrains.toolbox.api.remoteDev.environments.EnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.environments.ManualEnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.environments.SshEnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.ssh.SshConnectionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SshEnvironmentContentsViewFactory : EnvironmentContentsViewFactory {

    override suspend fun create(config: EnvironmentConfig, portProvider: () -> Int): EnvironmentContentsView {
        val ides = config.availableIdeProductCodes.map { productCode ->
            SimpleIdeStub(productCode)
        }

        val projects = config.projectPaths.map { path ->
            CachedProject(path)
        }

        return SimpleEnvironmentContentsView(ides, projects, config.username!!, config.sshKey!!, portProvider)
    }
}

/**
 * Immutable implementation.
 */
class SimpleEnvironmentContentsView(
    ides: List<CachedIdeStub>,
    projects: List<CachedProject>,
    val userName: String,
    val sshKey: String,
    private val portProvider: () -> Int
) : ManualEnvironmentContentsView, SshEnvironmentContentsView {

    // Expose as immutable flows - data is set once at construction
    override val ideListState: Flow<LoadableState<List<CachedIdeStub>>> =
        MutableStateFlow(LoadableState.Value(ides))

    override val projectListState: Flow<LoadableState<List<CachedProject>>> =
        MutableStateFlow(LoadableState.Value(projects))

    override suspend fun getConnectionInfo(): SshConnectionInfo = WorkspaceSshConnectionInfo(userName, sshKey, portProvider)
}

data class SimpleIdeStub(
    override val productCode: String,
    private val running: Boolean? = null
) : CachedIdeStub {
    override fun isRunning(): Boolean? = running
}

private class WorkspaceSshConnectionInfo(
    val uName: String,
    val sshKey: String,
    private val portProvider: () -> Int
) : SshConnectionInfo {

    override val host: String = "devspaces"

    // TODO: the port value is read on first call only.
    // But it's not re-read on the subsequent calls.
    // Need to re-read it on each connection attempts as the port is chosen dynamically.
    override val port: Int get() = portProvider()

    override val userName: String = uName

    override val sshConfig: String = "Host $host\n" +
            "  HostName 127.0.0.1\n" +
            "  UserKnownHostsFile /dev/null\n" +
            "  StrictHostKeyChecking no"

    override val privateKeys: List<ByteArray>
        get() = listOf(sshKey.toByteArray())

    override val shouldUseSystemConfiguration: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WorkspaceSshConnectionInfo

        if (host != other.host) return false
        if (port != other.port) return false

        return true
    }

    override fun hashCode(): Int {
        var result = port
        result = 31 * result + host.hashCode()
        return result
    }
}
