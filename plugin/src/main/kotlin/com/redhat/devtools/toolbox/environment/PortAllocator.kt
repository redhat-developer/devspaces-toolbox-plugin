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

/**
 * Manages local port assignments for environment connections.
 *
 * Toolbox queries [SshConnectionInfo.port] before [BeforeConnectionHook] runs,
 * but the local port is only known after establishing the port forward in the hook.
 * This creates a timing problem on first connection.
 *
 * Solution: on first connection, let the system assign a free port (portForward with 0),
 * then save it here. On subsequent connections, reuse the saved port so [SshConnectionInfo]
 * returns the correct value before the hook runs.
 */
object PortAllocator {
    private val allocatedPorts = mutableMapOf<String, Int>()

    fun get(environmentId: String): Int? = allocatedPorts[environmentId]

    fun save(environmentId: String, port: Int) {
        allocatedPorts[environmentId] = port
    }

    fun release(environmentId: String) {
        allocatedPorts.remove(environmentId)
    }
}
