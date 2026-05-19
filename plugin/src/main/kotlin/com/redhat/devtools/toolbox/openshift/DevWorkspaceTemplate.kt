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

import io.fabric8.kubernetes.api.model.GenericKubernetesResource

data class DevWorkspaceTemplate(
    val namespace: String,
    val name: String,
    val ownerReferenceUids: List<String>,
    val components: List<Map<String, Any>>
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(resource: GenericKubernetesResource): DevWorkspaceTemplate {
            val metadata = resource.metadata
            val spec = resource.additionalProperties["spec"] as? Map<*, *> ?: emptyMap<String, Any>()

            val ownerRefs = metadata.ownerReferences ?: emptyList()
            val ownerUids = ownerRefs
                .filter { ref ->
                    ref.apiVersion?.equals("workspace.devfile.io/v1alpha2", ignoreCase = true) == true &&
                    ref.kind?.equals("DevWorkspace", ignoreCase = true) == true
                }
                .mapNotNull { it.uid }

            val rawComponents = spec["components"]
            val components = if (rawComponents is List<*>) {
                rawComponents.filterIsInstance<Map<String, Any>>()
            } else {
                emptyList()
            }

            return DevWorkspaceTemplate(
                namespace = metadata.namespace ?: "",
                name = metadata.name ?: "",
                ownerReferenceUids = ownerUids,
                components = components
            )
        }
    }
}