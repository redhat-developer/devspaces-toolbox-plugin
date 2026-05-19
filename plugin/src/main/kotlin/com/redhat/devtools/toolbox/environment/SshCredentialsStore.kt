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
 * Stores SSH credentials (private key and username) fetched from workspace containers.
 *
 * Similar to [PortAllocator], this solves a timing issue: Toolbox queries
 * [SshConnectionInfo.privateKeys] and [SshConnectionInfo.userName] before
 * [BeforeConnectionHook] runs, but credentials are only available after
 * reading them from the container in the hook.
 *
 * On first connection, credentials are read from the pod and saved here.
 * On subsequent connections, the cached credentials are returned.
 */
object SshCredentialsStore {
    data class Credentials(
        val privateKey: String,
        val username: String
    )

    private val cache = mutableMapOf<String, Credentials>()

    fun get(environmentId: String): Credentials? = cache[environmentId]

    fun save(environmentId: String, credentials: Credentials) {
        cache[environmentId] = credentials
    }

    fun release(environmentId: String) {
        cache.remove(environmentId)
    }
}
