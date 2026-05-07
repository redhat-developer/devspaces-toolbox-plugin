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
package com.redhat.devtools.toolbox

import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.jetbrains.toolbox.api.core.util.LoadableState
import com.jetbrains.toolbox.api.localization.LocalizableStringFactory
import com.jetbrains.toolbox.api.remoteDev.states.RemoteEnvironmentState
import com.redhat.devtools.toolbox.datasource.DataSourceException
import com.redhat.devtools.toolbox.datasource.EnvironmentDataSource
import com.redhat.devtools.toolbox.environment.*
import com.redhat.devtools.toolbox.openshift.OpenShiftClientFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Repository managing the lifecycle of remote environments.
 * 
 * Responsibilities:
 * - Fetches environment configs from data sources
 * - Creates/updates RemoteEnvironment instances
 * - Manages background polling
 * - Exposes reactive state for the provider
 */
class EnvironmentRepository(
    private val dataSource: EnvironmentDataSource,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope,
    private val contentsViewFactory: EnvironmentContentsViewFactory = SshEnvironmentContentsViewFactory(),
    private val refreshInterval: Duration = 10.seconds,
    private val localizableStringFactory: LocalizableStringFactory,
    private val clientFactory: OpenShiftClientFactory
) {
    // Internal mutable state
    private val _environments = MutableStateFlow<LoadableState<List<DevSpacesRemoteEnvironment>>>(
        LoadableState.Loading
    )

    // Cache of created environments by ID - allows updating existing instances
    private val environmentCache = mutableMapOf<String, DevSpacesRemoteEnvironment>()

    // Observable environment list for the provider
    val environments: MutableStateFlow<LoadableState<List<DevSpacesRemoteEnvironment>>> = _environments

    fun startPolling() {
        coroutineScope.launch(CoroutineName("EnvironmentRepository-Polling")) {
            // Initial fetch
            refreshEnvironments()

            // periodically sync the workspaces list with the remote
            while (isActive) {
                delay(refreshInterval)
                refreshEnvironments()
            }
        }
    }

    /**
     * Triggers updating the environments list.
     *
     * @param externalEnvironment - optional, may be provided in case of an external request.
     * Typically, it comes from the Dashboard.
     */
    suspend fun refreshEnvironments(externalEnvironment: EnvironmentConfig? = null) {
        logger.debug("Refreshing environments from ${dataSource::class.simpleName}")

        try {
            val configs = dataSource.fetchEnvironments() + listOfNotNull(externalEnvironment)

            val environments = configs.map { config ->
                getOrCreateEnvironment(config)
            }

            // Remove environments that no longer exist
            val currentIds = configs.map { it.id }.toSet()
            environmentCache.keys.removeAll { it !in currentIds }

            _environments.value = LoadableState.Value(environments)
            logger.info("PLUGIN: Setting environments to ${environments.size} items: ${environments.map { it.id }}")

        } catch (e: CancellationException) {
            throw e
        } catch (e: DataSourceException) {
            logger.error("Data source error: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error: ${e.message}")
        }
    }

    /**
     * Gets an existing environment or creates a new one.
     * This preserves reactive subscriptions when refreshing.
     */
    private fun getOrCreateEnvironment(config: EnvironmentConfig): DevSpacesRemoteEnvironment {
        return environmentCache.getOrPut(config.id) {
            logger.debug("Creating new environment: ${config.id}")
            config.toRemoteEnvironment(contentsViewFactory, localizableStringFactory, logger, clientFactory)
        }.also { existingEnv ->
            // Update config if it is changed
            if (existingEnv.getConfig() != config) {
                logger.debug("Updating environment config: ${config.id}")
                existingEnv.updateConfig(config)
            }
        }
    }

    /**
     * Manually update a specific environment's state.
     * Useful for health check results, error reporting, etc.
     */
    fun updateEnvironmentState(
        environmentId: String, state: RemoteEnvironmentState, errorMessage: String? = null
    ) {
        environmentCache[environmentId]?.updateState(state, errorMessage)
            ?: logger.warn("Cannot update unknown environment: $environmentId")
    }

    /**
     * Allows to trigger programmatically local ThinClient connection to a remote environment.
     */
    fun updateConnectionRequest(
        environmentId: String, state: Boolean, errorMessage: String? = null
    ) {
        environmentCache[environmentId]?.updateConnectionRequest(state, errorMessage)
            ?: logger.warn("Cannot update connection request for unknown environment: $environmentId")
    }

    /**
     * Get a specific environment by ID.
     */
    fun getEnvironment(id: String): DevSpacesRemoteEnvironment? = environmentCache[id]
}