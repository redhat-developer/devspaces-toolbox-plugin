package com.redhat.devtools.toolbox.openshift

import com.jetbrains.toolbox.api.core.diagnostics.Logger
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.fabric8.openshift.client.OpenShiftClient

class OpenShiftClientFactory(
    private val logger: Logger
) {

    fun create(): OpenShiftClient {
        return try {
            KubernetesClientBuilder().build().adapt(OpenShiftClient::class.java)
        } catch (e: Exception) {
            logger.debug("Failed to build OpenShift client: ${e.message}")
            throw e
        }
    }
}