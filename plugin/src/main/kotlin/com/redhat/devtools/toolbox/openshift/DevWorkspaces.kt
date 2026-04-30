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
package com.redhat.devtools.toolbox.openshift

import com.jetbrains.toolbox.api.core.diagnostics.Logger
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext

class DevWorkspaces(private val client: KubernetesClient, private val logger: Logger? = null) {

    private val devWorkspaceContext = CustomResourceDefinitionContext.Builder()
        .withGroup("workspace.devfile.io")
        .withVersion("v1alpha2")
        .withPlural("devworkspaces")
        .withScope("Namespaced")
        .build()

    fun list(namespace: String): List<DevWorkspace> {
        return try {
            val resources = client.genericKubernetesResources(devWorkspaceContext)
                .inNamespace(namespace)
                .list()
                .items

            resources.map { resource -> DevWorkspace.from(resource) }
        } catch (e: KubernetesClientException) {
            logger?.info("API error listing DevWorkspaces in namespace $namespace: ${e.message}")

            when (e.code) {
                403, 404 -> emptyList()
                else -> {
                    logger?.error("Kubernetes API error ${e.code}: ${e.message}")
                    throw e
                }
            }
        }
    }
}