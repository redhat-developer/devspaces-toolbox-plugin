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
import kotlinx.coroutines.flow.StateFlow
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

    // Hide the Toolbox-provided `Delete` action in the `Environment actions` menu.
    // We don't need that for now.
    override val deleteActionFlow: StateFlow<(() -> Unit)?> = MutableStateFlow(null)

    override suspend fun getContentsView(): EnvironmentContentsView {
        logger.debug("PLUGIN: getContentsView called for id='${initialConfig.id}', name='${_currentConfig.name}'")
        val view = contentsViewFactory.create(
            _currentConfig,
            portProvider = { PortAllocator.get(initialConfig.id) ?: 0 },
            keyProvider = { SshKeyStore.get(initialConfig.id) ?: "" }
        )
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

                val namespace = _currentConfig.tags["namespace"]
                    ?: throw IllegalStateException("Namespace not found in environment config")
                val workspaceName = _currentConfig.name.value

                val client = clientFactory.create()
                activeClient = client

                val pod = client.pods()
                    .inNamespace(namespace)
                    .withLabel("controller.devfile.io/devworkspace_name", workspaceName)
                    .list()
                    .items
                    .firstOrNull()
                    ?: throw IllegalStateException("No pod found for workspace $workspaceName in namespace $namespace")

                logger.info("Found pod: ${pod.metadata.name} for workspace: $workspaceName")

                val savedPort = PortAllocator.get(initialConfig.id)
                activePortForward = client.pods()
                    .inNamespace(namespace)
                    .withName(pod.metadata.name)
                    .portForward(_currentConfig.port, savedPort ?: 0)

                if (savedPort == null) {
                    PortAllocator.save(initialConfig.id, activePortForward!!.localPort)
                }

                logger.info("Port forward established: localhost:${activePortForward?.localPort} -> pod:${_currentConfig.port}")



                // Read SSH key from container if not cached
                if (SshKeyStore.get(initialConfig.id) == null) {
                    val output = java.io.ByteArrayOutputStream()
                    val latch = java.util.concurrent.CountDownLatch(1)
                    client.pods()
                        .inNamespace(namespace)
                        .withName(pod.metadata.name)
                        .inContainer("jetbrains-sshd-page")
                        .writingOutput(output)
                        .usingListener(object : io.fabric8.kubernetes.client.dsl.ExecListener {
                            override fun onClose(code: Int, reason: String?) { latch.countDown() }
                            override fun onFailure(t: Throwable?, response: io.fabric8.kubernetes.client.dsl.ExecListener.Response?) { latch.countDown() }
                        })
                        .exec("cat", "/sshd/ssh_client_key")
                    latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    val sshKey = output.toString(Charsets.UTF_8)
                    SshKeyStore.save(initialConfig.id, sshKey)
                    logger.info("SSH key fetched from container for workspace: $workspaceName")
                }
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
