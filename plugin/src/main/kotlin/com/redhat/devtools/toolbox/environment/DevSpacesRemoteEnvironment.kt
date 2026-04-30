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

import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.jetbrains.toolbox.api.localization.LocalizableStringFactory
import com.jetbrains.toolbox.api.remoteDev.AfterDisconnectHook
import com.jetbrains.toolbox.api.remoteDev.BeforeConnectionHook
import com.jetbrains.toolbox.api.remoteDev.EnvironmentVisibilityState
import com.jetbrains.toolbox.api.remoteDev.RemoteProviderEnvironment
import com.jetbrains.toolbox.api.remoteDev.environments.EnvironmentContentsView
import com.jetbrains.toolbox.api.remoteDev.states.EnvironmentDescription
import com.jetbrains.toolbox.api.remoteDev.states.RemoteEnvironmentState
import com.jetbrains.toolbox.api.remoteDev.states.StandardRemoteEnvironmentState
import com.redhat.devtools.toolbox.openshift.OpenShiftClientFactory
import io.fabric8.kubernetes.client.LocalPortForward
import io.fabric8.openshift.client.OpenShiftClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reactive wrapper around [EnvironmentConfig] that satisfies the Toolbox API.
 */
class DevSpacesRemoteEnvironment(
    private val initialConfig: EnvironmentConfig,
    private val contentsViewFactory: EnvironmentContentsViewFactory = SshEnvironmentContentsViewFactory(),
    private val localizableStringFactory: LocalizableStringFactory,
    private val logger: Logger,
    private val clientFactory: OpenShiftClientFactory
) : RemoteProviderEnvironment(initialConfig.id) {

    // Mutable internal state
    private var _currentConfig: EnvironmentConfig = initialConfig
    private val _state = MutableStateFlow<RemoteEnvironmentState>(StandardRemoteEnvironmentState.Active)
    private val _description = MutableStateFlow<EnvironmentDescription>(
        EnvironmentDescription.General(initialConfig.description?.let { localizableStringFactory.ptrl(it) })
    )
    private var _visible: Boolean = false

    private val _connectionRequest = MutableStateFlow(false)

    // Port forwarding resources (kept alive during connection)
    private var activeClient: OpenShiftClient? = null
    private var activePortForward: LocalPortForward? = null

    //  Public reactive properties (observed by Toolbox UI)
    override var nameFlow: MutableStateFlow<String> = _currentConfig.name

    override val state: MutableStateFlow<RemoteEnvironmentState> = _state

    override val description: MutableStateFlow<EnvironmentDescription> = _description

    override val connectionRequest: Flow<Boolean> = _connectionRequest

    override suspend fun getContentsView(): EnvironmentContentsView {
        logger.debug("PLUGIN: getContentsView called for id='${initialConfig.id}', name='${_currentConfig.name}'")
        val view = contentsViewFactory.create(_currentConfig) { activePortForward?.localPort ?: 2022 }
        logger.debug("PLUGIN: Created view with ${_currentConfig.availableIdeProductCodes.size} IDEs, ${_currentConfig.projectPaths.size} projects")
        return view
    }

    override fun setVisible(visibilityState: EnvironmentVisibilityState) {
        _visible = visibilityState.statusVisible
    }

    // Update methods (called by Repository)
    fun updateState(newState: RemoteEnvironmentState, errorMessage: String? = null) {
        _state.update { newState }
        if (errorMessage != null) {
            _description.update { EnvironmentDescription.General(localizableStringFactory.ptrl(errorMessage)) }
        }
    }

    fun updateConnectionRequest(newState: Boolean, errorMessage: String? = null) {
        _connectionRequest.update { newState }
        if (errorMessage != null) {
            _description.update { EnvironmentDescription.General(localizableStringFactory.ptrl(errorMessage)) }
        }
    }

    fun updateConfig(newConfig: EnvironmentConfig) {
        require(newConfig.id == initialConfig.id) { logger.info("Cannot change environment ID for ${initialConfig.id}") }
        _currentConfig = newConfig
        _description.update {
            EnvironmentDescription.General(newConfig.description?.let { text ->
                localizableStringFactory.ptrl(text)
            })
        }
    }

    fun getConfig(): EnvironmentConfig = _currentConfig

    override fun getBeforeConnectionHooks(): List<BeforeConnectionHook> {
        return listOf(
            BeforeConnectionHook {
                logger.info("Running before connection hook for environment: ${initialConfig.id}")

                // Close any previous port forward
//                closePortForward()

                // Create new client and port forward (kept alive until closePortForward is called)
                val client = clientFactory.create()
                activeClient = client

                // TODO: figure out how to fetch the pod information
                activePortForward = client.pods()
                    .inNamespace("azatsarynnyy-che")
                    .withName("workspacee64b5c5b8a6d4196-577c7ff96b-plbhx")
                    .portForward(_currentConfig.port, 0)

                logger.info("Port forward established: localhost:${activePortForward?.localPort} -> pod:${_currentConfig.port}")
            }
        )
    }

    override fun getAfterDisconnectHooks(): List<AfterDisconnectHook> {
        return listOf(
            object : AfterDisconnectHook {
                override fun afterDisconnect(isManual: Boolean) {
                    logger.info("Running after disconnect hook for environment: ${initialConfig.id}")
                    closePortForward()
                }
            }
        )
    }

    fun closePortForward() {
        try {
            activePortForward?.close()
            activePortForward = null
            logger.info("Port forward closed")
        } catch (e: Exception) {
            logger.debug("Error closing port forward: ${e.message}")
        }

        try {
            activeClient?.close()
            activeClient = null
        } catch (e: Exception) {
            logger.debug("Error closing client: ${e.message}")
        }
    }
}

/**
 * Extension function to create RemoteEnvironment from config.
 */
fun EnvironmentConfig.toRemoteEnvironment(
    factory: EnvironmentContentsViewFactory = SshEnvironmentContentsViewFactory(),
    localizableStringFactory: LocalizableStringFactory,
    logger: Logger,
    clientFactory: OpenShiftClientFactory
): DevSpacesRemoteEnvironment {
    return DevSpacesRemoteEnvironment(this, factory, localizableStringFactory, logger, clientFactory)
}
