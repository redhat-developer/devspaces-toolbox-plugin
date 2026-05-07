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
package com.redhat.devtools.toolbox.datasource

import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.redhat.devtools.toolbox.environment.EnvironmentConfig
import com.redhat.devtools.toolbox.openshift.OpenShiftClientFactory
import com.redhat.devtools.toolbox.openshift.DevWorkspaces
import com.redhat.devtools.toolbox.openshift.Projects
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Data source returns the environment configurations from
 * the DevWorkspaces fetched from a Dev Spaces instance.
 */
class DevWorkspacesDataSource(
    private val clientFactory: OpenShiftClientFactory,
    private val logger: Logger
) : EnvironmentDataSource {

    /**
     * Fetches the CDEs from the currently logged-in Dev Spaces instance.
     */
    override suspend fun fetchEnvironments(): List<EnvironmentConfig> {
        return try {
            clientFactory.create().use { client ->
                val projects = Projects(client).list()

                projects
                    .mapNotNull { it.metadata?.name }
                    .flatMap { namespace ->
                        DevWorkspaces(client, logger).list(namespace)
                    }
                    .map { workspace ->
                        // TODO: figure out how to fetch the connection data
                        EnvironmentConfig(
                            id = workspace.name,
                            name = MutableStateFlow(workspace.name),
                            description = "[API] DevWorkspace",
                            username = "1001270000",
                            port = 2022,
                            availableIdeProductCodes = listOf("IU"),
                            projectPaths = listOf("/projects"),
                            tags = mapOf("namespace" to workspace.namespace)
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch environments: ${e.message}")
            emptyList()
        }
    }

    override fun handleExternalRequest(
        id: String, name: String, userName: String, sshKey: String, projects: List<String>
    ): EnvironmentConfig {
        return EnvironmentConfig(
            id = id,
            name = MutableStateFlow(name),
            description = "[External] DevWorkspace",
            username = userName,
            sshKey = sshKey,
            availableIdeProductCodes = listOf("IU"),
            projectPaths = projects
        )
    }
}