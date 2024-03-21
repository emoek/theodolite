package theodolite.benchmark

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apps.DaemonSet
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.kubernetes.api.model.apps.StatefulSet
import io.fabric8.kubernetes.api.model.batch.v1.Job
import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import rocks.theodolite.kubernetes.K8sManager

private var SLEEP_TIME_MS = 500L
class RolloutManager(private val blockUntilResourcesReady: Boolean, private val client: NamespacedKubernetesClient) {

    fun rollout(resources: List<HasMetadata>) {
        resources
            .forEach { K8sManager(client).deploy(it) }

        if (blockUntilResourcesReady) {
            resources
                .forEach {
                    when (it) {
                        is Deployment -> waitFor { client.apps().deployments().withName(it.metadata.name).isReady }
                        is StatefulSet -> waitFor { client.apps().statefulSets().withName(it.metadata.name).isReady }
                        is DaemonSet -> waitFor { client.apps().daemonSets().withName(it.metadata.name).isReady }
                        is ReplicaSet -> waitFor { client.apps().replicaSets().withName(it.metadata.name).isReady }
                        is Job -> waitFor { client.batch().v1().cronjobs().withName(it.metadata.name).isReady }
                    }
                }
        }
    }

    fun rollout(resources: List<HasMetadata>, desiredReplicas: Map<String, Int>) {
        resources.forEach{resource ->
            when(resource) {
                is Deployment -> {
                    val currentDeployment = client.apps().deployments().withName(resource.metadata.name).get()
                    val currentReplicas = currentDeployment.spec.replicas ?: 1
                    val newReplicas = desiredReplicas[resource.metadata.name] ?: currentReplicas
                    if (newReplicas != currentReplicas) {
                        val deployment = client.apps().deployments().withName(resource.metadata.name).edit()
                        deployment.spec.replicas = newReplicas
                        client.apps().deployments().createOrReplace(deployment)
//                        client.apps().deployments().withName(resource.metadata.name)
//                                .edit().editSpec().withReplicas(newReplicas).endSpec().done()
                    }
                }
                is StatefulSet -> {
                    val currentStatefulSet = client.apps().statefulSets().withName(resource.metadata.name).get()
                    val currentReplicas = currentStatefulSet.spec.replicas ?: 1
                    val newReplicas = desiredReplicas[resource.metadata.name] ?: currentReplicas
                    if (newReplicas != currentReplicas) {
                        val deployment = client.apps().statefulSets().withName(resource.metadata.name).edit()
                        deployment.spec.replicas = newReplicas
                        client.apps().statefulSets().createOrReplace(deployment)
//                        client.apps().statefulSets().withName(resource.metadata.name)
//                                .edit().editSpec().withReplicas(newReplicas).endSpec().done()
                    }
                }
            }

        }
    }

        private fun waitFor(isResourceReady: () -> Boolean) {
        while (!isResourceReady()) {
            Thread.sleep(SLEEP_TIME_MS)
        }
    }

}