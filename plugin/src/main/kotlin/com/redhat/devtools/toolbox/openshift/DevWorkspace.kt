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

data class DevWorkspace(
    val namespace: String,
    val name: String,
    val uid: String,
    val started: Boolean,
    val phase: String
) {
    val running: Boolean
        get() = phase == "Running"

    companion object {
        fun from(resource: GenericKubernetesResource): DevWorkspace {
            val metadata = resource.metadata
            val spec = resource.additionalProperties["spec"] as? Map<*, *> ?: emptyMap<String, Any>()
            val status = resource.additionalProperties["status"] as? Map<*, *> ?: emptyMap<String, Any>()

            return DevWorkspace(
                namespace = metadata.namespace ?: "",
                name = metadata.name ?: "",
                uid = metadata.uid ?: "",
                started = spec["started"] as? Boolean ?: false,
                phase = status["phase"] as? String ?: ""
            )
        }
    }
}