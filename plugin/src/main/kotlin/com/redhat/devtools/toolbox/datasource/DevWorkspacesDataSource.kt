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
import kotlinx.coroutines.flow.MutableStateFlow
import com.redhat.devtools.toolbox.environment.EnvironmentConfig

/**
 * Data source returns the environment configurations from
 * the DevWorkspaces fetched from a Dev Spaces instance.
 */
class DevWorkspacesDataSource(
    logger: Logger
) : EnvironmentDataSource {

    /**
     * Fetches the CDEs from the currently logged-in Dev Spaces instance.
     */
    override suspend fun fetchEnvironments(): List<EnvironmentConfig> {
//        TODO("Not yet implemented")
        return emptyList()
    }

    override fun handleExternalRequest(
        id: String, name: String, sshKey: String, projects: List<String>
    ): EnvironmentConfig {
        return EnvironmentConfig(
            id = id,
            name = MutableStateFlow(name),
            description = "[External] DevWorkspace",
            sshKey = sshKey,
            availableIdeProductCodes = listOf("IU"),
            projectPaths = projects
        )
    }
}
