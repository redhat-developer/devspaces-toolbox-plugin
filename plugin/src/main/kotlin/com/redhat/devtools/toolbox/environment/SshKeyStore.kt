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
 * Stores SSH private keys fetched from workspace containers.
 *
 * Similar to [PortAllocator], this solves a timing issue: Toolbox queries
 * [SshConnectionInfo.privateKeys] before [BeforeConnectionHook] runs,
 * but the key is only available after reading it from the container in the hook.
 *
 * On first connection, the key is read from the pod and saved here.
 * On subsequent connections, the cached key is returned.
 */
object SshKeyStore {
    private val keys = mutableMapOf<String, String>()

    fun get(environmentId: String): String? = keys[environmentId]

    fun save(environmentId: String, key: String) {
        keys[environmentId] = key
    }

    fun release(environmentId: String) {
        keys.remove(environmentId)
    }
}
