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

import com.redhat.devtools.toolbox.environment.EnvironmentConfig

/**
 * Note: Returns [EnvironmentConfig], not [com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment].
 *
 * The [com.redhat.devtools.toolbox.EnvironmentRepository] is responsible for creating
 * [com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment] instances from configs.
 *  This separation allows:
 * - Data sources to be pure data fetchers
 * - Caching/lifecycle management in the repository
 * - Easy testing of data sources without UI dependencies
 */
interface EnvironmentDataSource {

    /**
     * Fetches current environment configurations.
     *
     * @throws DataSourceException on failure
     */
    suspend fun fetchEnvironments(): List<EnvironmentConfig>

    /**
     * Returns an additional environment configuration
     * came from external request.
     */
    fun handleExternalRequest(id: String, name: String, sshKey: String, projects: List<String>): EnvironmentConfig
}

/**
 * Exception wrapper for data source errors.
 */
class DataSourceException(
    message: String, cause: Throwable? = null
) : Exception(message, cause)
