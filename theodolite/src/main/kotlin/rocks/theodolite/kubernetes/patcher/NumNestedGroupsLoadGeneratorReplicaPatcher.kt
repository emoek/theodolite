package rocks.theodolite.kubernetes.patcher

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.apps.Deployment
import kotlin.math.pow

class NumNestedGroupsLoadGeneratorReplicaPatcher(
    private val numSensors: Int,
    private val loadGenMaxRecords: Int,
) : AbstractIntPatcher() {

    override fun patchSingleResource(resource: HasMetadata, value: Int): HasMetadata {
        if (resource is Deployment) {
            val approxNumSensors = numSensors.toDouble().pow(value.toDouble())
            val loadGenInstances =
                (approxNumSensors + loadGenMaxRecords.toDouble() - 1) / loadGenMaxRecords.toDouble()
            resource.spec.replicas = loadGenInstances.toInt()

        }
        return resource
    }
}

