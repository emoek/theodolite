package rocks.theodolite.kubernetes.slo

import rocks.theodolite.core.IOHandler
import rocks.theodolite.kubernetes.model.KubernetesBenchmark
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

private val DEFAULT_STEP_SIZE = Duration.ofSeconds(5)
private val DEFAULT_STAGE = listOf("load")
private val DEFAULT_WORKLOAD = "workload"
private val DEFAULT_WORKLOADURL = "prometheus"

class StageBasedAnalysisExecutor(
        private val slo: KubernetesBenchmark.Slo,
        private val executionId: Int
) {

    private val fetcher = MetricFetcher(
            prometheusURL = slo.prometheusUrl,
            offset = Duration.ofHours(slo.offset.toLong())
    )

    private val ioHandler = IOHandler()


    /**
     *  Collects an experiment via prometheus or loki data during provided stages.
     *  First fetches data, then documents them.
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments and the stage
     *  @return true if the experiment succeeded.
     */
    fun collect(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>) {
        var repetitionCounter = 1

        try {
//            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"





            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE

            val stages = slo.properties["stages"]?.lowercase()?.split("+") ?: DEFAULT_STAGE






            if (slo.prometheusUrl.contains("loki")) {
                executionIntervals.forEach { intervalList ->
                    intervalList.forEach { (stage, start, end) ->



                        if (stage in listOf("base", "idle", "load") && stages.contains(stage)) {


                            val lokiData = fetcher.fetchLogs(
                                    start = start,
                                    end = end,
                                    stepSize = stepSize,
                                    query = SloConfigHandler.getQueryString(slo = slo)
                            )

                            if (lokiData.data?.result.isNullOrEmpty()) {
                                println("The loki collection query for stage $stage did not provide any results! ")

                            } else {

                                ioHandler.writeToCSVFile(
                                        fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter}",
                                        data = lokiData.getResultAsList(),
                                        columns = listOf("labels", "timestamp", "value")
                                )
                            }


                        }
                    }
                    repetitionCounter++
                }

            } else {

                executionIntervals.forEach { intervalList ->
                    intervalList.forEach { (stage, start, end) ->



                        if (stage in listOf("base", "idle", "load") && stages.contains(stage)) {


                            val prometheusData = fetcher.fetchMetric(
                                    start = start,
                                    end = end,
                                    stepSize = stepSize,
                                    query = SloConfigHandler.getQueryString(slo = slo)
                            )

                            if (prometheusData.data?.result.isNullOrEmpty()) {
                                println("The prometheus collection query for stage $stage did not provide any results! ")
                            } else {

                                ioHandler.writeToCSVFile(
                                        fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter}",
                                        data = prometheusData.getAllResultAsList(),
                                        columns = listOf("labels", "timestamp", "value")
                                )
                            }


                        }
                    }
                    repetitionCounter++
                }
            }




        } catch (e: Exception) {
            throw EvaluationFailedException("Collection failed for resource '$resource' and load '$load ", e)
        }
    }


    /**
     *  Analyses an experiment via prometheus and loki data.
     *  First fetches data, and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments and stage.
     *  @return true if the experiment succeeded.
     */
    fun analyzeEfficiency(load: Int, resource: Int, executionIntervals: List<List<Triple<String, Instant, Instant>>>): Boolean {

        try {


            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE

            val workload = slo.properties["workloadQuery"]?.lowercase() ?: DEFAULT_WORKLOAD


            val workloadUrl = slo.properties["workloadUrl"]?.lowercase() ?: DEFAULT_WORKLOADURL




            val total: MutableList<Triple<Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>>> = mutableListOf()
            val totalWithLogs: MutableList<Triple<Triple<String,PrometheusResponse,LokiResponse>,Triple<String,PrometheusResponse,LokiResponse>,Triple<String,PrometheusResponse,LokiResponse>>> = mutableListOf()

            executionIntervals.forEach { intervalList ->
                val loadTime: Triple<String,Instant,Instant> = intervalList[2]
                val idleTime: Triple<String,Instant,Instant> = intervalList[1]
                val baseTime: Triple<String,Instant,Instant> = intervalList[0]


                var workloadDataBase = PrometheusResponse()
                var workloadDataIdle = PrometheusResponse()
                var workloadDataLoad = PrometheusResponse()
                var logWorkloadDataBase = LokiResponse()
                var logWorkloadDataIdle = LokiResponse()
                var logWorkloadDataLoad = LokiResponse()

                val prometheusDataLoad = fetcher.fetchMetric(
                        start = loadTime.second,
                        end = loadTime.third,
                        stepSize = stepSize,
                        query = SloConfigHandler.getQueryString(slo = slo)
                )

                val prometheusDataIdle = fetcher.fetchMetric(
                        start = idleTime.second,
                        end = idleTime.third,
                        stepSize = stepSize,
                        query = SloConfigHandler.getQueryString(slo = slo)
                )

                val prometheusDataBase = fetcher.fetchMetric(
                        start = baseTime.second,
                        end = baseTime.third,
                        stepSize = stepSize,
                        query = SloConfigHandler.getQueryString(slo = slo)
                )

                if (prometheusDataLoad.data?.result.isNullOrEmpty()) {
                    println("The Prometheus query did not provide any results for the load stage!")
                }
                if (prometheusDataLoad.data?.result.isNullOrEmpty() && prometheusDataIdle.data?.result.isNullOrEmpty() && prometheusDataBase.data?.result.isNullOrEmpty()) {
                    throw NoSuchFieldException("The prometheus query did not provide any result for all stages.")

                }


                if (workload != DEFAULT_WORKLOAD) {


                    if (workloadUrl != DEFAULT_WORKLOADURL) {
                        val fetcher = MetricFetcher(
                                prometheusURL = workloadUrl,
                                offset = Duration.ofHours(slo.offset.toLong())
                        )
                        logWorkloadDataLoad = fetcher.fetchLogs(
                                start = loadTime.second,
                                end = loadTime.third,
                                stepSize = stepSize,
                                query = workload
                        )

                        logWorkloadDataIdle = fetcher.fetchLogs(
                                start = idleTime.second,
                                end = idleTime.third,
                                stepSize = stepSize,
                                query = workload
                        )

                        logWorkloadDataBase = fetcher.fetchLogs(
                                start = baseTime.second,
                                end = baseTime.third,
                                stepSize = stepSize,
                                query = workload
                        )

                        if (logWorkloadDataLoad.data?.result.isNullOrEmpty()) {
                            println("The loki query did not provide any result for the load stage which is a necessity.")
                        }
                    } else {

                        workloadDataLoad = fetcher.fetchMetric(
                                start = loadTime.second,
                                end = loadTime.third,
                                stepSize = stepSize,
                                query = workload
                        )



                        workloadDataIdle = fetcher.fetchMetric(
                                start = idleTime.second,
                                end = idleTime.third,
                                stepSize = stepSize,
                                query = workload
                        )

                        workloadDataBase = fetcher.fetchMetric(
                                start = baseTime.second,
                                end = baseTime.third,
                                stepSize = stepSize,
                                query = workload
                        )

                        if (workloadDataLoad.data?.result.isNullOrEmpty()) {
                            println("The prometheus query did not provide any result for the workload query in load stage which is a necessity.")
                        }
                    }

                }



                if (workloadUrl != DEFAULT_WORKLOADURL) {
                    totalWithLogs.add(Triple(Triple(baseTime.first, prometheusDataBase,logWorkloadDataBase), Triple(idleTime.first,prometheusDataIdle, logWorkloadDataIdle), Triple(loadTime.first,prometheusDataLoad,logWorkloadDataLoad)))


                } else {
                    total.add(Triple(Triple(baseTime.first, prometheusDataBase,workloadDataBase), Triple(idleTime.first,prometheusDataIdle, workloadDataIdle), Triple(loadTime.first,prometheusDataLoad,workloadDataLoad)))

                }
            }


            val sloChecker = SloCheckerFactory().create(
            sloType = slo.sloType,
            properties = slo.properties,
            load = load,
            resources = resource
            )

            if (workloadUrl != DEFAULT_WORKLOADURL) {
                return sloChecker.evaluateLogAndStageBased(totalWithLogs,load)
            } else {
                return sloChecker.evaluateStageBased(total, load)
            }



        } catch (e: Exception) {
            throw EvaluationFailedException("Evaluation failed for resource '$resource' and load '$load ", e)
        }
    }





    /**
     *  Analyses an experiment via prometheus.
     *  First fetches data, document the data, and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments and stage.
     *  @return true if the experiment succeeded.
     */
    fun analyze(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>): Boolean {
        var repetitionCounter = 1

        try {
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"



            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE




            val prometheusDataList: MutableList<PrometheusResponse> = mutableListOf()
            executionIntervals.forEach { intervalList ->
                intervalList.forEach { (stage, start, end) ->



                    if (stage in listOf("load")) {
                        val prometheusData = fetcher.fetchMetric(
                                start = start,
                                end = end,
                                stepSize = stepSize,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )
                        prometheusDataList.add(prometheusData)


                        if (prometheusData.data?.result.isNullOrEmpty()) {
                            throw NoSuchFieldException("The prometheus query did not provide any result for the load stage which is a necessity.")

                        }
                        ioHandler.writeToCSVFile(
                                fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter}",
                                data = prometheusData.getResultAsList(),
                                columns = listOf("labels", "timestamp", "value")
                        )
                    }
                }
                repetitionCounter++
            }

            val sloChecker = SloCheckerFactory().create(
                    sloType = slo.sloType,
                    properties = slo.properties,
                    load = load,
                    resources = resource
            )

            return sloChecker.evaluate(prometheusDataList)




        } catch (e: Exception) {
            throw EvaluationFailedException("Evaluation failed for resource '$resource' and load '$load ", e)
        }
    }
    private val NONLATIN: Pattern = Pattern.compile("[^\\w-]")
    private val WHITESPACE: Pattern = Pattern.compile("[\\s]")

    private fun String.toSlug(): String {
        val noWhitespace: String = WHITESPACE.matcher(this).replaceAll("-")
        val normalized: String = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD)
        val slug: String = NONLATIN.matcher(normalized).replaceAll("")
        return slug.lowercase(Locale.ENGLISH)
    }

}