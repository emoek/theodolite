package theodolite.execution.operator

import io.fabric8.kubernetes.client.NamespacedKubernetesClient
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import mu.KotlinLogging
import theodolite.benchmark.BenchmarkExecution
import theodolite.benchmark.KubernetesBenchmark
import theodolite.execution.TheodoliteExecutor
import java.lang.Thread.sleep
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * The controller implementation for Theodolite.
 *
 * Maintains a Dequeue, based on ConcurrentLinkedDequeue, of executions to be executed for a benchmark.
 *
 * @param client The NamespacedKubernetesClient
 * @param executionContext The CustomResourceDefinitionContext
 *
 * @see NamespacedKubernetesClient
 * @see CustomResourceDefinitionContext
 * @see BenchmarkExecution
 * @see KubernetesBenchmark
 * @see ConcurrentLinkedDeque
 */
class TheodoliteController(
    val client: NamespacedKubernetesClient,
    val executionContext: CustomResourceDefinitionContext,
    val path: String
) {
    lateinit var executor: TheodoliteExecutor
    val executionsQueue: ConcurrentLinkedDeque<BenchmarkExecution> = ConcurrentLinkedDeque()
    val benchmarks: ConcurrentHashMap<String, KubernetesBenchmark> = ConcurrentHashMap()
    var isUpdated = AtomicBoolean(false)

    /**
     * Runs the TheodoliteController forever.
     */
    fun run() {
        while (true) {
            try {
                reconcile()
                logger.info { "Theodolite is waiting for new matching benchmark and execution." }
                logger.info { "Currently available executions: " }
                executionsQueue.forEach {
                    logger.info { "${it.name} : waiting for : ${it.benchmark}" }
                }
                logger.info { "Currently available benchmarks: " }
                benchmarks.forEach {
                    logger.info { it.key }
                }
                sleep(2000)
            } catch (e: InterruptedException) {
                logger.error { "Execution interrupted with error: $e." }
            }
        }
    }

    /**
     * Ensures that the application state corresponds to the defined KubernetesBenchmarks and BenchmarkExecutions.
     *
     * @see KubernetesBenchmark
     * @see BenchmarkExecution
     */
    @Synchronized
    private fun reconcile() {
        while (executionsQueue.isNotEmpty()) {
            val execution = executionsQueue.peek()
            val benchmark = benchmarks[execution.benchmark]

            if (benchmark == null) {
                logger.debug { "No benchmark found for execution ${execution.name}." }
                sleep(1000)
            } else {
                runExecution(execution, benchmark)
            }
        }
    }

    /**
     * Execute a benchmark with a defined KubernetesBenchmark and BenchmarkExecution
     *
     * @see KubernetesBenchmark
     * @see BenchmarkExecution
     */
    @Synchronized
    fun runExecution(execution: BenchmarkExecution, benchmark: KubernetesBenchmark) {
        isUpdated.set(false)
        benchmark.path = path
        logger.info { "Start execution ${execution.name} with benchmark ${benchmark.name}." }
        executor = TheodoliteExecutor(config = execution, kubernetesBenchmark = benchmark)
        executor.run()

        try {
            if (!isUpdated.get()) {
                this.executionsQueue.removeIf { e -> e.name == execution.name }
                client.customResource(executionContext).delete(client.namespace, execution.metadata.name)
            }
        } catch (e: Exception) {
            logger.warn { "Deletion skipped." }
        }

        logger.info { "Execution of ${execution.name} is finally stopped." }
    }

    /**
     * @return true if the TheodoliteExecutor of this controller is initialized. Else returns false.
     *
     * @see TheodoliteExecutor
     */
    @Synchronized
    fun isInitialized(): Boolean {
        return ::executor.isInitialized
    }
}
