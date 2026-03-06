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

    override suspend fun create(config: EnvironmentConfig): EnvironmentContentsView {
        val ides = config.availableIdeProductCodes.map { productCode ->
            SimpleIdeStub(productCode)
        }

        val projects = config.projectPaths.map { path ->
            CachedProject(path)
        }

        return SimpleEnvironmentContentsView(ides, projects, config.sshKey!!)
    }
}

/**
 * Immutable implementation.
 */
class SimpleEnvironmentContentsView(
    ides: List<CachedIdeStub>,
    projects: List<CachedProject>,
    val sshKey: String
) : ManualEnvironmentContentsView, SshEnvironmentContentsView {

    // Expose as immutable flows - data is set once at construction
    override val ideListState: Flow<LoadableState<List<CachedIdeStub>>> =
        MutableStateFlow(LoadableState.Value(ides))

    override val projectListState: Flow<LoadableState<List<CachedProject>>> =
        MutableStateFlow(LoadableState.Value(projects))

    override suspend fun getConnectionInfo(): SshConnectionInfo = WorkspaceSshConnectionInfo(sshKey)
}

data class SimpleIdeStub(
    override val productCode: String,
    private val running: Boolean? = null
) : CachedIdeStub {
    override fun isRunning(): Boolean? = running
}

private class WorkspaceSshConnectionInfo(val sshKey: String) : SshConnectionInfo {

    // localhost as we forward remote port to a local system
    override val host: String = "127.0.0.1"

    override val port: Int = 2022

    override val userName: String = "1001270000"

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
