package rocks.theodolite.kubernetes

import rocks.theodolite.kubernetes.model.KubernetesBenchmark
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 *  A BenchmarkDeployment contains the necessary infrastructure to execute a benchmark.
 *  Therefore it has the capabilities to set up the deployment of a benchmark and to tear it down.
 */
interface BenchmarkDeployment {

    /**
     * Setup a benchmark. This method is responsible for deploying the resources of a benchmark.
     */
    fun setup(stage: String)

    /**
     *  Tears down a benchmark. This method is responsible for deleting the deployed
     *  resources and to reset the used infrastructure.
     */
    fun teardown()

    /**
     *  Tears down a benchmark. This method is responsible for deleting the deployed
     *  resources and to reset the used infrastructure.
     */
    fun teardownNonResources()
}
