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
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.client.dsl.base.PatchContext
import io.fabric8.kubernetes.client.dsl.base.PatchType
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DevWorkspaces(private val client: KubernetesClient, private val logger: Logger) {

    private val devWorkspaceContext = CustomResourceDefinitionContext.Builder()
        .withGroup("workspace.devfile.io")
        .withVersion("v1alpha2")
        .withPlural("devworkspaces")
        .withScope("Namespaced")
        .build()

    private val devWorkspaceTemplateContext = CustomResourceDefinitionContext.Builder()
        .withGroup("workspace.devfile.io")
        .withVersion("v1alpha2")
        .withPlural("devworkspacetemplates")
        .withScope("Namespaced")
        .build()

    /**
     * Lists DevWorkspaces filtered to only Toolbox-compatible workspaces.
     */
    fun list(namespace: String): List<DevWorkspace> {
        return try {
            val resources = client.genericKubernetesResources(devWorkspaceContext)
                .inNamespace(namespace)
                .list()
                .items

            val workspaces = resources.map { resource -> DevWorkspace.from(resource) }
            val templateMap = getDevWorkspaceTemplateMap(namespace)

            workspaces.filter { isToolboxCompatible(it, templateMap) }
        } catch (e: KubernetesClientException) {
            logger.info("API error listing DevWorkspaces in namespace $namespace: ${e.message}")

            when (e.code) {
                // This is mostly for handling two cases:
                // 1) there might be some namespaces (OpenShift projects) in which the user is not allowed to list the resources "devworkspaces", e.g. "openshift-virtualization-os-images" on Red Hat Dev Sandbox
                // 2) the cluster doesn't have the RedHat DevSpaces operator installed
                //
                // In those cases, it doesn't make much sense to show an error to the user, so let's skip it silently by returning an empty workspaces list.
                403, 404 -> emptyList()
                else -> {
                    logger.error("Kubernetes API error ${e.code}: ${e.message}")
                    throw e
                }
            }
        }
    }

    private fun getDevWorkspaceTemplateMap(namespace: String): Map<String, List<DevWorkspaceTemplate>> {
        return try {
            val resources = client.genericKubernetesResources(devWorkspaceTemplateContext)
                .inNamespace(namespace)
                .list()
                .items

            resources
                .map { DevWorkspaceTemplate.from(it) }
                .flatMap { template ->
                    template.ownerReferenceUids.map { uid -> uid to template }
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second }
                )
        } catch (e: KubernetesClientException) {
            logger.debug("Error fetching DevWorkspaceTemplates: ${e.message}")
            emptyMap()
        }
    }

    private fun isToolboxCompatible(
        workspace: DevWorkspace,
        templateMap: Map<String, List<DevWorkspaceTemplate>>
    ): Boolean {
        val templates = templateMap[workspace.uid] ?: return false
        return templates.any { template ->
            template.components.any { component ->
                val attributes = component["attributes"] as? Map<*, *>
                attributes?.get("app.kubernetes.io/component") == "jetbrains-sshd"
            }
        }
    }

    fun get(namespace: String, name: String): DevWorkspace {
        val resource = client.genericKubernetesResources(devWorkspaceContext)
            .inNamespace(namespace)
            .withName(name)
            .get()
            ?: throw KubernetesClientException("DevWorkspace '$name' not found in namespace '$namespace'")

        return DevWorkspace.from(resource)
    }

    fun start(namespace: String, name: String) {
        val patch = """[{"op":"replace","path":"/spec/started","value":true}]"""
        client.genericKubernetesResources(devWorkspaceContext)
            .inNamespace(namespace)
            .withName(name)
            .patch(PatchContext.of(PatchType.JSON), patch)
        logger.info("Started DevWorkspace '$name' in namespace '$namespace'")
    }

    suspend fun startAndWait(
        namespace: String,
        name: String,
        timeoutSeconds: Long = 300
    ): Boolean {
        val workspace = get(namespace, name)

        if (!workspace.started) {
            start(namespace, name)
        }

        return waitUntilRunning(namespace, name, timeoutSeconds)
    }

    suspend fun waitUntilRunning(
        namespace: String,
        name: String,
        timeoutSeconds: Long = 300
    ): Boolean {
        return withTimeoutOrNull(timeoutSeconds.seconds) {
            while (true) {
                val workspace = try {
                    get(namespace, name)
                } catch (e: Exception) {
                    logger.debug("Error fetching workspace status: ${e.message}")
                    delay(500.milliseconds)
                    continue
                }

                when (workspace.phase) {
                    DevWorkspace.PHASE_RUNNING -> return@withTimeoutOrNull true
                    DevWorkspace.PHASE_FAILED -> return@withTimeoutOrNull false
                }

                delay(500.milliseconds)
            }

            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
    }
}