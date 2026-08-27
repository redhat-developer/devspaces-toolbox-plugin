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
    val id: String,
    val uid: String,
    val started: Boolean,
    val phase: String,
    val cheEditor: String?,
    /**
     * On-disk path of every project the workspace clones, e.g.
     * `["/projects/frontend", "/projects/backend"]`. Empty when the
     * workspace declares no projects.
     */
    val projectPaths: List<String> = emptyList()
) {
    val running: Boolean
        get() = phase == PHASE_RUNNING

    companion object {
        const val PHASE_RUNNING = "Running"
        const val PHASE_STOPPED = "Stopped"
        const val PHASE_STARTING = "Starting"
        const val PHASE_STOPPING = "Stopping"
        const val PHASE_FAILED = "Failed"

        /**
         * Default root under which the DevWorkspace engine clones projects.
         * Matches the DevWorkspace Operator's `PROJECTS_ROOT` default.
         */
        const val PROJECTS_ROOT = "/projects"

        fun from(resource: GenericKubernetesResource): DevWorkspace {
            val metadata = resource.metadata
            val spec = resource.additionalProperties["spec"] as? Map<*, *> ?: emptyMap<String, Any>()
            val status = resource.additionalProperties["status"] as? Map<*, *> ?: emptyMap<String, Any>()
            val cheEditor = metadata.annotations?.get("che.eclipse.org/che-editor")

            return DevWorkspace(
                namespace = metadata.namespace ?: "",
                name = metadata.name ?: "",
                id = status["devworkspaceId"] as? String ?: "",
                uid = metadata.uid ?: "",
                started = spec["started"] as? Boolean ?: false,
                phase = status["phase"] as? String ?: "",
                cheEditor = cheEditor,
                projectPaths = parseProjectPaths(spec)
            )
        }

        /**
         * Resolves the clone path of every project declared by the workspace.
         *
         * Projects live in `spec.template.projects[]`. The DevWorkspace engine
         * clones each one into `<PROJECTS_ROOT>/<clonePath ?: name>`; an absolute
         * `clonePath` is honored verbatim. A project without a usable name or
         * clonePath is skipped. Returns an empty list when none are declared.
         */
        private fun parseProjectPaths(spec: Map<*, *>): List<String> {
            val template = spec["template"] as? Map<*, *> ?: return emptyList()
            val projects = template["projects"] as? List<*> ?: return emptyList()
            return projects
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { project ->
                    val subPath = (project["clonePath"] as? String)
                        ?: (project["name"] as? String)
                        ?: return@mapNotNull null
                    if (subPath.startsWith("/")) subPath else "$PROJECTS_ROOT/$subPath"
                }
        }
    }
}