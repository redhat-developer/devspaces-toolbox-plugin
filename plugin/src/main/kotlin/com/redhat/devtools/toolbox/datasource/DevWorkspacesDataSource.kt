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
 * the DevWorkspaces fetched from the current Dev Spaces instance.
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
                        EnvironmentConfig(
                            id = workspace.id,
                            name = MutableStateFlow(workspace.name),
                            description = workspace.phase,
                            port = 2022, // the port of in-container running sshd
//                            availableIdeProductCodes = listOf("IU"),
                            // TODO: implement fetching the PROJECT_SOURCES env. var. value
                            projectPaths = listOf("/projects"),
                            tags = mapOf("namespace" to workspace.namespace)
                        )
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to fetch environments: ${e.message}")
            throw DataSourceException(e.message.toString(), e)
        }
    }
}
