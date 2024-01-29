package rocks.theodolite.kubernetes.slo

import rocks.theodolite.core.IOHandler
import rocks.theodolite.kubernetes.model.KubernetesBenchmark.Slo
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

private val DEFAULT_STEP_SIZE = Duration.ofSeconds(5)
private val DEFAULT_WORKLOAD = "workload"
private val DEFAULT_WORKLOADURL = "prometheus"




/**
 * Contains the analysis. Fetches a metric from Prometheus, documents it, and evaluates it.
 * @param slo Slo that is used for the analysis.
 */
class AnalysisExecutor(
        private val slo: Slo,
        private val executionId: Int
) {

    private val fetcher = MetricFetcher(
            prometheusURL = slo.prometheusUrl,
            offset = Duration.ofHours(slo.offset.toLong())
    )

    private val ioHandler = IOHandler()






    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun collect(load: Int, resource: Int, executionIntervals: List<Pair<Instant, Instant>>) {
        var repetitionCounter = 1

        try {
//            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"





            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE






            if (slo.prometheusUrl.contains("loki")) {
                executionIntervals.forEach { intervalList ->





                            // ADAPT FETCH METRIC SO THAT ALL PODS/CONTAINERS ARE PROVIDED FOR ANALYSIS
                            val lokiData = fetcher.fetchLogs(
                                    start = intervalList.first,
                                    end = intervalList.second,
                                    stepSize = stepSize,
                                    query = SloConfigHandler.getQueryString(slo = slo)
                            )

                            if (lokiData.data?.result.isNullOrEmpty()) {
                                throw NoSuchFieldException("The prometheus query did not provide any result for the query.")

                            }
                            ioHandler.writeToCSVFile(
                                    fileURL = "${fileURL}_${slo.name}_${repetitionCounter}",
                                    data = lokiData.getResultAsList(),
                                    columns = listOf("labels", "timestamp", "value")
                            )




                    repetitionCounter++
                }

            } else {

                executionIntervals.forEach { intervalList ->





                            // ADAPT FETCH METRIC SO THAT ALL PODS/CONTAINERS ARE PROVIDED FOR ANALYSIS
                            val prometheusData = fetcher.fetchMetric(
                                    start = intervalList.first,
                                    end = intervalList.second,
                                    stepSize = stepSize,
                                    query = SloConfigHandler.getQueryString(slo = slo)
                            )

                            if (prometheusData.data?.result.isNullOrEmpty()) {
                                throw NoSuchFieldException("The prometheus query did not provide any result for the query.")

                            }
                            ioHandler.writeToCSVFile(
                                    fileURL = "${fileURL}_${slo.name}_${repetitionCounter}",
                                    data = prometheusData.getAllResultAsList(),
                                    columns = listOf("labels", "timestamp", "value")
                            )




                    repetitionCounter++
                }
            }




        } catch (e: Exception) {
            throw EvaluationFailedException("Collection failed for resource '$resource' and load '$load ", e)
        }
    }











    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun analyze(load: Int, resource: Int, executionIntervals: List<Pair<Instant, Instant>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"

            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE

            val prometheusData = executionIntervals
                    .map { interval ->
                        fetcher.fetchMetric(
                                start = interval.first,
                                end = interval.second,
                                stepSize = stepSize,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )
                    }
            if (prometheusData.isEmpty()) {
                throw NoSuchFieldException("The prometheus query did not provide any result for the query.")

            }
            prometheusData.forEach{ data ->
                ioHandler.writeToCSVFile(
                        fileURL = "${fileURL}_${slo.name}_${repetitionCounter++}",
                        data = data.getResultAsList(),
                        columns = listOf("labels", "timestamp", "value")
                )
            }

            val sloChecker = SloCheckerFactory().create(
                    sloType = slo.sloType,
                    properties = slo.properties,
                    load = load,
                    resources = resource
            )

            return sloChecker.evaluate(prometheusData)

        } catch (e: Exception) {
            throw EvaluationFailedException("Evaluation failed for resource '$resource' and load '$load ", e)
        }
    }








    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun analyzeEfficiency(load: Int, resource: Int, executionIntervals: List<Pair<Instant, Instant>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"

            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE
            val workload = slo.properties["workloadQuery"]?.lowercase() ?: DEFAULT_WORKLOAD
            val workloadUrl = slo.properties["workloadUrl"]?.lowercase() ?: DEFAULT_WORKLOADURL



            val prometheusData = executionIntervals
                    .map { interval ->
                        fetcher.fetchMetric(
                                start = interval.first,
                                end = interval.second,
                                stepSize = stepSize,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )
                    }

//            prometheusData.forEach{ data ->
//                ioHandler.writeToCSVFile(
//                        fileURL = "${fileURL}_${slo.name}_${repetitionCounter++}",
//                        data = data.getResultAsList(),
//                        columns = listOf("labels", "timestamp", "value")
//                )
//            }

            var lokiData = listOf<LokiResponse>()
            var workloadData = listOf<PrometheusResponse>()
            if (workload != DEFAULT_WORKLOAD) {
                if (workloadUrl != DEFAULT_WORKLOADURL) {

                    val logFetcher = MetricFetcher(
                            prometheusURL = workloadUrl,
                            offset = Duration.ofHours(slo.offset.toLong())
                    )
                    lokiData = executionIntervals
                            .map { interval ->
                                logFetcher.fetchLogs(
                                        start = interval.first,
                                        end = interval.second,
                                        stepSize = stepSize,
                                        query = workload
                                )
                            }

//                    lokiData.forEach{ data ->
//                        ioHandler.writeToCSVFile(
//                                fileURL = "${fileURL}_${slo.name}_${repetitionCounter++}",
//                                data = data.getResultAsList(),
//                                columns = listOf("labels", "timestamp", "value")
//                        )
//                    }




                } else {

                    workloadData = executionIntervals
                            .map { interval ->
                                fetcher.fetchMetric(
                                        start = interval.first,
                                        end = interval.second,
                                        stepSize = stepSize,
                                        query = workload
                                )
                            }



                }
            }

            val sloChecker = SloCheckerFactory().create(
                    sloType = slo.sloType,
                    properties = slo.properties,
                    load = load,
                    resources = resource
            )

            if (workloadUrl != DEFAULT_WORKLOADURL) {
                val total : Pair<List<PrometheusResponse>, List<LokiResponse>> = Pair(prometheusData,lokiData)
                return sloChecker.evaluateLogEfficiency(total,load)

            } else {
                val total : Pair<List<PrometheusResponse>, List<PrometheusResponse>> = Pair(prometheusData,workloadData)
                return sloChecker.evaluateEfficiency(total,load)

            }

        } catch (e: Exception) {
            throw EvaluationFailedException("Evaluation failed for resource '$resource' and load '$load ", e)
        }
    }









    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun analyzeEfficiency(loads: List<Int>, resources: List<Int>, executionIntervals: List<Pair<Instant, Instant>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${slo.sloType.toSlug()}"

            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE
            val workload = slo.properties["workloadQuery"]?.lowercase() ?: DEFAULT_WORKLOAD
            val workloadUrl = slo.properties["workloadUrl"]?.lowercase() ?: DEFAULT_WORKLOADURL



            val prometheusData = executionIntervals
                    .map { interval ->
                        fetcher.fetchMetric(
                                start = interval.first,
                                end = interval.second,
                                stepSize = stepSize,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )
                    }

//            prometheusData.forEach{ data ->
//                ioHandler.writeToCSVFile(
//                        fileURL = "${fileURL}_${slo.name}_${repetitionCounter++}",
//                        data = data.getResultAsList(),
//                        columns = listOf("labels", "timestamp", "value")
//                )
//            }

            var lokiData = listOf<LokiResponse>()
            var workloadData = listOf<PrometheusResponse>()
            if (workload != DEFAULT_WORKLOAD) {
                if (workloadUrl != DEFAULT_WORKLOADURL) {

                    val logFetcher = MetricFetcher(
                            prometheusURL = workloadUrl,
                            offset = Duration.ofHours(slo.offset.toLong())
                    )

                    lokiData = executionIntervals
                            .map { interval ->
                                logFetcher.fetchLogs(
                                        start = interval.first,
                                        end = interval.second,
                                        stepSize = stepSize,
                                        query = workload
                                )
                            }

//                    lokiData.forEach{ data ->
//                        ioHandler.writeToCSVFile(
//                                fileURL = "${fileURL}_${slo.name}_${repetitionCounter++}",
//                                data = data.getResultAsList(),
//                                columns = listOf("labels", "timestamp", "value")
//                        )
//                    }




                } else {

                    workloadData = executionIntervals
                            .map { interval ->
                                fetcher.fetchMetric(
                                        start = interval.first,
                                        end = interval.second,
                                        stepSize = stepSize,
                                        query = workload
                                )
                            }



                }
            }

            val sloChecker = SloCheckerFactory().create(
                    sloType = slo.sloType,
                    properties = slo.properties,
                    load = 0,
                    resources = 0
            )

            val meanLoad = loads.average().toInt()
            val meanResource = resources.average().toInt()
            if (workloadUrl != DEFAULT_WORKLOADURL) {
                val total : Pair<List<PrometheusResponse>, List<LokiResponse>> = Pair(prometheusData,lokiData)
                return sloChecker.evaluateLogEfficiency(total,meanLoad)

            } else {
                val total : Pair<List<PrometheusResponse>, List<PrometheusResponse>> = Pair(prometheusData,workloadData)
                return sloChecker.evaluateEfficiency(total,meanLoad)

            }

        } catch (e: Exception) {
            throw EvaluationFailedException("Evaluation failed for resources and loads ", e)
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


