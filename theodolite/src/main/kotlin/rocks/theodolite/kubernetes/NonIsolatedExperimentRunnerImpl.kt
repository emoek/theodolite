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
class NonIsolatedExperimentRunnerImpl(
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


    override fun runExperiment(loads: List<Int>, resources: List<Int>) : Boolean {
        var result = false
        val executionIntervals: MutableList<Pair<Instant, Instant>> = ArrayList()


        for (i in 1.rangeTo(repetitions)) {
            logger.info { "Wait ${this.loadGenerationDelay} seconds before starting repetition ${i}." }
            Thread.sleep(Duration.ofSeconds(this.loadGenerationDelay).toMillis())
            if (this.run.get()) {
                logger.info { "Run repetition $i/$repetitions" }
                executionIntervals.add(
                        runSingleExperiment(
                                loads, resources
                        )
                )
            } else {
                break
            }
        }

        val (collectSlos, analysisSlos) = slos.partition { it.sloType.lowercase() == "collect" }
        val (efficiencySlos, scalabilitySlos) = analysisSlos.partition { it.sloType.lowercase() == "efficiency" }
        val experimentResults: MutableList<Boolean> = mutableListOf()


        if(this.run.get()) {

            if (collectSlos.isNotEmpty()) {

                collectSlos.map {
                    AnalysisExecutor(slo = it, executionId = executionId)
                            .collect(
                                    load = 0,
                                    resource = 0,
                                    executionIntervals = executionIntervals
                            )

                }
            }

            if (scalabilitySlos.isNotEmpty()) {

                val scalabilityResults = scalabilitySlos.map {
                    AnalysisExecutor(slo = it, executionId = executionId)
                            .analyze(0, 0, executionIntervals)
                }
                experimentResults.addAll(scalabilityResults)
            }



            if (efficiencySlos.isNotEmpty()) {
                logger.info { "Wait ${this.loadGenerationDelay} seconds for the logs before starting the Analysis Executor." }
                Thread.sleep(Duration.ofSeconds(this.loadGenerationDelay).toMillis())
                val efficiencyResults = efficiencySlos.map {
                    AnalysisExecutor(slo = it, executionId = executionId)
                            .analyzeEfficiency(loads, resources, executionIntervals)
                }
                experimentResults.addAll(efficiencyResults)

            }



            result = (false !in experimentResults)
            this.results.addExperimentResult(Pair(loads.average().toInt(), resources.average().toInt()), result)




        } else {
            throw ExecutionFailedException("The execution was interrupted")
        }


        return result
    }
    override fun runExperiment(load: Int, resource: Int): Boolean {
        return true
    }


    private fun runSingleExperiment(loads: List<Int>, resources: List<Int>): Pair<Instant, Instant> {
        var counter = 0
//        var benchmarkDeployment: BenchmarkDeployment

        val resourcesLength = resources.size

        val firstLoad = loads[0]
        val firstResource = resources[0]
        val loads = loads.drop(1)
        val resources = resources.drop(1)




        val start = Instant.now()
        loads.zip(resources).forEach { (load, resource) ->
            println("Load: $load, Resource: $resource")


            val benchmarkDeployment = benchmarkDeploymentBuilder.buildDeployment(
                    firstLoad,
                    this.loadPatcherDefinitions,
                    firstResource,
                    this.resourcePatcherDefinitions,
                    this.configurationOverrides,
                    this.loadGenerationDelay,
                    this.afterTeardownDelay,
                    this.waitForResourcesEnabled
            )








            try {
//                benchmarkDeployment.setup("")


                if (counter != 0) {

                    benchmarkDeployment.setReplicas(resource)


                    benchmarkDeployment.setup("generate")
                } else {
                    benchmarkDeployment.setup("")

                }


                this.waitAndLog()




                if (mode == ExecutionModes.OPERATOR.value) {
                    eventCreator.createEvent(
                            executionName = executionName,
                            type = "NORMAL",
                            reason = "Start experiment",
                            message = "load: $load, resources: $resource"
                    )
                }


            } catch (e: Exception) {
                this.run.set(false)

                if (mode == ExecutionModes.OPERATOR.value) {
                    eventCreator.createEvent(
                            executionName = executionName,
                            type = "WARNING",
                            reason = "Start experiment failed",
                            message = "load: $load, resources: $resource"
                    )
                }
                throw ExecutionFailedException("Error during setup the experiment", e)
            }

            try {
                counter++

                if (counter == resourcesLength) {
                    benchmarkDeployment.teardown()

                } else {
                    benchmarkDeployment.teardownNonApp()
                }
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

        }

        val end = Instant.now()

        return Pair(start,end)
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