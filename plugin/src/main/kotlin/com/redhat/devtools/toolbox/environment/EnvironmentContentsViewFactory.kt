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

import com.jetbrains.toolbox.api.remoteDev.environments.EnvironmentContentsView

/**
 * Interface for creating environment content views.
 *
 * Different implementations may support different integration types:
 * - [com.jetbrains.toolbox.api.remoteDev.environments.ManualEnvironmentContentsView]: Static lists that we control
 * - [com.jetbrains.toolbox.api.remoteDev.environments.SshEnvironmentContentsView]: Toolbox handles SSH connection
 * - [com.jetbrains.toolbox.api.remoteDev.environments.AgentConnectionBasedEnvironmentContentsView]: Direct connection to Toolbox agent
 * - [com.jetbrains.toolbox.api.remoteDev.environments.PortForwardingCapableEnvironmentContentsView]
 */
fun interface EnvironmentContentsViewFactory {

    /**
     * Creates the appropriate contents view for the given environment.
     *
     * @param config The environment's configuration data
     * @return An EnvironmentContentsView implementation
     */
    suspend fun create(config: EnvironmentConfig, portProvider: () -> Int): EnvironmentContentsView
}
