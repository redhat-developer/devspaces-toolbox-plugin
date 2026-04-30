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

import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.OpenShiftClient

class Projects(private val client: OpenShiftClient) {

    fun list(): List<Project> {
        return client.projects().list().items
    }
}