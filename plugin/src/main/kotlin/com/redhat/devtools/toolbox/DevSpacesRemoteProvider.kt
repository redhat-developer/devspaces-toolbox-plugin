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
import java.net.URI

/**
 * [RemoteProvider] implementation that delegates the environment management to [EnvironmentRepository].
 */
class DevSpacesRemoteProvider(
    val repository: EnvironmentRepository,
    val localizableStringFactory: LocalizableStringFactory,
    val logger: Logger
) : RemoteProvider("Dev Spaces") {

    override val iconResource: ImageResource = jvmResourceReader().image("/icon.svg")

    override val noEnvironmentsDescription: String = "No DevWorkspaces found. Create a new one from the Dev Spaces Dashboard"
    override val loadingEnvironmentsDescription: LocalizableString = localizableStringFactory.ptrl("Loading the Workspaces list...\r\n\r\n" +
            "If the list does not load within a few seconds -\r\n" +
            "make sure you are logged in to the correct cluster\r\n" +
            "by running the 'oc login ...' command in the terminal.")

    override val environments: MutableStateFlow<LoadableState<List<DevSpacesRemoteEnvironment>>> =
        repository.environments

    override val canCreateNewEnvironments: Boolean = false
    override val isSingleEnvironment: Boolean = false

    override fun setVisible(visibilityState: ProviderVisibilityState) {}

    override fun getNewEnvironmentUiPage(): UiPage = UiPage(localizableStringFactory.pnotr("Choose a Workspace to connect to"))

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

        if (dwID.isBlank()) {
            logger.warn("Received URI without valid dwID parameter: $uri")
            return
        }

        // Schedule establishing the connection to the environment.
        repository.updateConnectionRequest(dwID, true)
    }

    override fun close() {}
}
