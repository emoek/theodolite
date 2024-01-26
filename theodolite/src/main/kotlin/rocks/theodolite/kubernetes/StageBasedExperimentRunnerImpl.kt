package rocks.theodolite.kubernetes

import io.quarkus.runtime.annotations.RegisterForReflection
import mu.KotlinLogging
import rocks.theodolite.core.ExperimentRunner
import rocks.theodolite.core.Results
import rocks.theodolite.kubernetes.model.KubernetesBenchmark
import rocks.theodolite.kubernetes.operator.EventCreator
import rocks.theodolite.kubernetes.patcher.PatcherDefinition
import rocks.theodolite.kubernetes.slo.AnalysisExecutor
import rocks.theodolite.kubernetes.slo.StageBasedAnalysisExecutor
import rocks.theodolite.kubernetes.util.ConfigurationOverride
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RegisterForReflection
class StageBasedExperimentRunnerImpl(
        results: Results,
        private val benchmarkDeploymentBuilder: BenchmarkDeploymentBuilder,
        private val executionDuration: Duration,
        private val configurationOverrides: List<ConfigurationOverride?>,
        private val slos: List<KubernetesBenchmark.Slo>,
        private val repetitions: Int,
        private val executionId: Int,
        private val loadGenerationDelay: Long,
        private val afterTeardownDelay: Long,
        private val executionName: String,
        private val loadPatcherDefinitions: List<PatcherDefinition>,
        private val resourcePatcherDefinitions: List<PatcherDefinition>,
        private val waitForResourcesEnabled: Boolean
) : ExperimentRunner(
        results
) {
    private val eventCreator = EventCreator()
    private val mode = Configuration.EXECUTION_MODE

    override fun runExperiment(load: Int, resource: Int): Boolean {
        var result = false
//        val executionIntervals: MutableList<Pair<Instant, Instant>> = ArrayList()
        val executionIntervals: MutableList<MutableList<Triple<String, Instant, Instant>>> = ArrayList()


        for (i in 1.rangeTo(repetitions)) {
            if (this.run.get()) {
                logger.info { "Run repetition $i/$repetitions" }
                executionIntervals.add(
                        runSingleExperiment(
                                load, resource
                        )
                )
            } else {
                break
            }
        }
        /**
         * Analyse the experiment, if [run] is true, otherwise the experiment was canceled by the user.
         */
        if (this.run.get()) {



            val (collectSlos, analysisSlos) = slos.partition { it.sloType.lowercase() == "collect" }
            val (efficiencySlos, scalabilitySlos) = analysisSlos.partition { it.sloType.lowercase() == "efficiency" }





            collectSlos.map {
                StageBasedAnalysisExecutor(slo = it, executionId = executionId)
                        .collect(
                                load = load,
                                resource = resource,
                                executionIntervals = executionIntervals
                        )

            }


            val experimentResults: MutableList<Boolean> = mutableListOf()

            if (scalabilitySlos.isNotEmpty()) {
                val scalabilityResults = scalabilitySlos.map {
                    StageBasedAnalysisExecutor(slo = it, executionId = executionId)
                            .analyze(
                                    load = load,
                                    resource = resource,
                                    executionIntervals = executionIntervals
                            )
                }
                experimentResults.addAll(scalabilityResults)
            }


            if (efficiencySlos.isNotEmpty()) {
                logger.info { "Wait ${this.loadGenerationDelay} seconds for the logs before starting the Analysis Executor." }
                Thread.sleep(Duration.ofSeconds(this.loadGenerationDelay).toMillis())
                val efficiencyResults = efficiencySlos.map {
                    StageBasedAnalysisExecutor(slo = it, executionId = executionId)
                            .analyzeEfficiency(
                                    load = load,
                                    resource = resource,
                                    executionIntervals = executionIntervals
                            )
                }

                experimentResults.addAll(efficiencyResults)
            }


            result = (false !in experimentResults)
            this.results.addExperimentResult(Pair(load, resource), result)
        } else {
            throw ExecutionFailedException("The execution was interrupted")
        }
        return result
    }





    private fun runSingleExperiment(load: Int, resource: Int): MutableList<Triple<String, Instant, Instant>> {
        val benchmarkDeployment = benchmarkDeploymentBuilder.buildDeployment(
                load,
                this.loadPatcherDefinitions,
                resource,
                this.resourcePatcherDefinitions,
                this.configurationOverrides,
                this.loadGenerationDelay,
                this.afterTeardownDelay,
                this.waitForResourcesEnabled
        )


        val fromBase: Instant
        val fromIdle: Instant
        val fromLoad: Instant
        val timestamps: MutableList<Triple<String, Instant, Instant>> = mutableListOf()

        try {

            logger.info { "Wait ${this.loadGenerationDelay} seconds before starting the Infrastructure operations." }
            Thread.sleep(Duration.ofSeconds(this.loadGenerationDelay).toMillis())
            benchmarkDeployment.setup("base")




            fromBase = Instant.now()

            this.waitAndLog()




            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "NORMAL",
                        reason = "Start experiment Base Stage",
                        message = "load: $load, resources: $resource"
                )
            }
        } catch (e: Exception) {
            this.run.set(false)

            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "WARNING",
                        reason = "Start experiment Base Stage failed",
                        message = "load: $load, resources: $resource"
                )
            }
            throw ExecutionFailedException("Error during setup the experiment", e)
        }

        val toBase = Instant.now()
        timestamps.add(Triple("base",fromBase,toBase))




        try {

            logger.info { "Wait ${this.loadGenerationDelay} seconds before starting the SUT." }
            Thread.sleep(Duration.ofSeconds(this.loadGenerationDelay).toMillis())
            benchmarkDeployment.setup("idle")




            fromIdle = Instant.now()

            this.waitAndLog()




            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "NORMAL",
                        reason = "Start experiment Idle Stage",
                        message = "load: $load, resources: $resource"
                )
            }
        } catch (e: Exception) {
            this.run.set(false)

            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "WARNING",
                        reason = "Start experiment Idle Stage failed",
                        message = "load: $load, resources: $resource"
                )
            }
            throw ExecutionFailedException("Error during setup the experiment", e)
        }

        val toIdle = Instant.now()
        timestamps.add(Triple("idle",fromIdle,toIdle))






        try {

            logger.info { "Wait ${this.loadGenerationDelay} seconds before starting the load generator." }
            Thread.sleep(Duration.ofSeconds(this.loadGenerationDelay).toMillis())
            benchmarkDeployment.setup("load")




            fromLoad = Instant.now()

            this.waitAndLog()




            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "NORMAL",
                        reason = "Start experiment Load Stage",
                        message = "load: $load, resources: $resource"
                )
            }
        } catch (e: Exception) {
            this.run.set(false)

            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "WARNING",
                        reason = "Start experiment Load Stage failed",
                        message = "load: $load, resources: $resource"
                )
            }
            throw ExecutionFailedException("Error during setup the experiment", e)
        }

        val toLoad = Instant.now()
        timestamps.add(Triple("load",fromLoad,toLoad))












        try {
            benchmarkDeployment.teardown()
            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "NORMAL",
                        reason = "Stop experiment",
                        message = "Teardown complete"
                )
            }
        } catch (e: Exception) {
            if (mode == ExecutionModes.OPERATOR.value) {
                eventCreator.createEvent(
                        executionName = executionName,
                        type = "WARNING",
                        reason = "Stop experiment failed",
                        message = "Teardown failed: ${e.message}"
                )
            }
            throw ExecutionFailedException("Error during teardown the experiment", e)
        }
        return timestamps
    }

    /**
     * Wait while the benchmark is running and log the number of minutes executed every 1 minute.
     */
    fun waitAndLog() {
        logger.info { "Execution of a new stage started." }

        var secondsRunning = 0L

        while (run.get() && secondsRunning < executionDuration.toSeconds()) {
            secondsRunning++
            Thread.sleep(Duration.ofSeconds(1).toMillis())

            if ((secondsRunning % 60) == 0L) {
                logger.info { "Executed: ${secondsRunning / 60} minutes." }
            }
        }

        logger.debug { "Executor shutdown gracefully." }

    }
}