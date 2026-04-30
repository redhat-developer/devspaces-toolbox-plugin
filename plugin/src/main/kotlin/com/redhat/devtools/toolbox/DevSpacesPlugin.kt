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

import com.jetbrains.toolbox.api.core.ServiceLocator
import com.jetbrains.toolbox.api.core.diagnostics.Logger
import com.jetbrains.toolbox.api.localization.LocalizableStringFactory
import com.jetbrains.toolbox.api.remoteDev.RemoteDevExtension
import com.jetbrains.toolbox.api.remoteDev.RemoteProvider
import kotlinx.coroutines.CoroutineScope
import com.redhat.devtools.toolbox.datasource.EnvironmentDataSource
import com.redhat.devtools.toolbox.datasource.DevWorkspacesDataSource
import com.redhat.devtools.toolbox.openshift.OpenShiftClientFactory

/**
 * Extends Toolbox remote development subsystem with
 * the functionality for connecting to CDEs running in Dev Spaces.
 *
 * Architecture:
 * ```
 * DataSource ──▶ EnvironmentConfig ──▶ Repository ──▶ RemoteEnvironment ──▶ Provider ──▶ Toolbox UI
 *                                          │
 *                                          └── Manages lifecycle, caching, updates
 * ```
 **/
class DevSpacesRemoteDevExtension : RemoteDevExtension {

    override fun createRemoteProviderPluginInstance(serviceLocator: ServiceLocator): RemoteProvider {
        val logger = serviceLocator.getService(Logger::class.java)
        val coroutineScope = serviceLocator.getService(CoroutineScope::class.java)
        val localizableStringFactory = serviceLocator.getService(LocalizableStringFactory::class.java)

        val clientFactory = OpenShiftClientFactory(logger)

        // Single data source, swap implementation as needed
        val dataSource = createDataSource(logger, clientFactory)

        // Initialized and manages the remote environments
        val repository = EnvironmentRepository(
            dataSource = dataSource,
            coroutineScope = coroutineScope,
            logger = logger,
            localizableStringFactory = localizableStringFactory,
            clientFactory  = clientFactory
        )

        // Periodically refresh environments from the data source
        repository.startPolling()

        logger.info("DevSpacesRemoteProvider initialized with ${dataSource::class.simpleName}")
        return DevSpacesRemoteProvider(repository, logger)
    }

    private fun createDataSource(logger: Logger, clientFactory: OpenShiftClientFactory): EnvironmentDataSource {
        return DevWorkspacesDataSource(clientFactory, logger)
    }
}
