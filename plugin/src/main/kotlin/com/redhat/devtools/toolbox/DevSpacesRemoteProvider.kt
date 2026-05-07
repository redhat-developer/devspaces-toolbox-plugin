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
import com.jetbrains.toolbox.api.localization.LocalizableString
import com.jetbrains.toolbox.api.localization.LocalizableStringFactory
import com.jetbrains.toolbox.api.remoteDev.ProviderVisibilityState
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider
import com.jetbrains.toolbox.api.ui.components.UiPage
import com.jetbrains.toolbox.platform.image.ImageResource
import com.jetbrains.toolbox.platform.image.image
import com.jetbrains.toolbox.platform.resource.jvm.jvmResourceReader
import kotlinx.coroutines.flow.MutableStateFlow
import com.redhat.devtools.toolbox.environment.DevSpacesRemoteEnvironment
import com.redhat.devtools.toolbox.environment.EnvironmentConfig
import java.net.URI

/**
 * [RemoteProvider] implementation that delegates the environment management to [EnvironmentRepository].
 */
class DevSpacesRemoteProvider(
    val repository: EnvironmentRepository, val localizableStringFactory: LocalizableStringFactory, val logger: Logger
) : RemoteProvider("Dev Spaces") {

    override val iconResource: ImageResource = jvmResourceReader().image("/icon.svg")

    override val noEnvironmentsDescription: String = "No DevWorkspaces found. Create a new one from the Dev Spaces Dashboard"
    override val loadingEnvironmentsDescription: LocalizableString = localizableStringFactory.ptrl("Loading DevWorkspaces...")

    override val environments: MutableStateFlow<LoadableState<List<DevSpacesRemoteEnvironment>>> =
        repository.environments

    override val canCreateNewEnvironments: Boolean = false
    override val isSingleEnvironment: Boolean = false

    override fun setVisible(visibilityState: ProviderVisibilityState) {}

    override fun getNewEnvironmentUiPage(): UiPage = UiPage(localizableStringFactory.pnotr("Choose a DevWorkspace to connect to"))

    /**
     * Handles an external request, typically comes from Che/DevSpaces Dashboard via the link as:
     *      jetbrains://gateway/com.redhat.devtools.toolbox?...
     *
     * 'jetbrainsd' local service handles such links by intercepting them and forwarding to the Toolbox.
     */
    override suspend fun handleUri(uri: URI) {
        logger.info("DevSpacesRemoteProvider received URI: $uri")

        val queryParams = uri.query?.split("&")?.associate { param ->
            val parts = param.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        } ?: emptyMap()

        val dwID: String = queryParams["dwID"] ?: ""
        val dwName: String = queryParams["dwName"] ?: ""
        val userName: String = queryParams["username"] ?: ""
        val sshKey: String = queryParams["key"] ?: ""
        val project: String = queryParams["project"] ?: ""

        // re-read the environments list with adding the additional env.
        repository.refreshEnvironments(
            EnvironmentConfig(
                id = dwID,
                name = MutableStateFlow(dwName),
                username = userName,
                sshKey = sshKey,
                projectPaths = listOf(project),
            )
        )

        // Schedule connecting to a CDE from a Thin Client
        // once the environment is added to Toolbox.
        repository.updateConnectionRequest(dwID, true, "Error while connecting to remote")
    }

    override fun close() {}
}
