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

    override suspend fun create(
        config: EnvironmentConfig,
        portProvider: () -> Int,
        keyProvider: () -> String
    ): EnvironmentContentsView {
        val ides = config.availableIdeProductCodes.map { productCode ->
            SimpleIdeStub(productCode)
        }

        val projects = config.projectPaths.map { path ->
            CachedProject(path)
        }

        return SimpleEnvironmentContentsView(ides, projects, config.username!!, portProvider, keyProvider)
    }
}

/**
 * Immutable implementation.
 */
class SimpleEnvironmentContentsView(
    ides: List<CachedIdeStub>,
    projects: List<CachedProject>,
    private val userName: String,
    private val portProvider: () -> Int,
    private val keyProvider: () -> String
) : ManualEnvironmentContentsView, SshEnvironmentContentsView {

    // Expose as immutable flows - data is set once at construction
    override val ideListState: Flow<LoadableState<List<CachedIdeStub>>> =
        MutableStateFlow(LoadableState.Value(ides))

    override val projectListState: Flow<LoadableState<List<CachedProject>>> =
        MutableStateFlow(LoadableState.Value(projects))

    override suspend fun getConnectionInfo(): SshConnectionInfo = WorkspaceSshConnectionInfo(userName, portProvider, keyProvider)
}

data class SimpleIdeStub(
    override val productCode: String,
    private val running: Boolean? = null
) : CachedIdeStub {
    override fun isRunning(): Boolean? = running
}

private class WorkspaceSshConnectionInfo(
    val uName: String,
    private val portProvider: () -> Int,
    private val keyProvider: () -> String
) : SshConnectionInfo {

    override val host: String = "devspaces"

    override val port: Int get() = portProvider()

    override val userName: String = uName

    override val sshConfig: String = "Host $host\n" +
            "  HostName 127.0.0.1\n" +
            "  UserKnownHostsFile /dev/null\n" +
            "  StrictHostKeyChecking no"

    override val privateKeys: List<ByteArray>
        get() = listOf(keyProvider().toByteArray())

    override val shouldUseSystemConfiguration: Boolean = false

    override fun equals(other: Any?): Boolean {
        // Before establishing an SSH connection to a specific environment,
        // Toolbox stores the `privateKeys` value to ~/Library/Caches/JetBrains/Toolbox/ssh_keys/LS0tLS1CRUdJTiBP
        // and passes the flie path to `ssh -i` command line.
        //
        // With a proper `equals` implementation, Toolbox doesn't refresh an SSH key stored in the temp cache file.
        //
        // This is the only way I found so far to make the multiconnection mode work well.
        // So, it updates an SSH key in the temp file for each new connection.
        return false
    }
}
