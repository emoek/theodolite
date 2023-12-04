package rocks.theodolite.kubernetes.slo

import rocks.theodolite.core.IOHandler
import rocks.theodolite.kubernetes.model.KubernetesBenchmark.Slo
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

private val DEFAULT_STEP_SIZE = Duration.ofSeconds(5)
private val DEFAULT_STAGE = listOf("load")
private val DEFAULT_WORKLOAD = "workload"



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









    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun analyzeEfficiency(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"



            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE
            val workload = slo.properties["workloadQuery"]?.lowercase() ?: DEFAULT_WORKLOAD





            val totalDataListWithQuery: MutableList<Pair<Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>>> = mutableListOf()



            val totalDataList: MutableList<Pair<Pair<String,PrometheusResponse>,Pair<String,PrometheusResponse>>> = mutableListOf()



            executionIntervals.forEach { intervalList ->
                val dataListWithQuery: MutableList<Triple<String,PrometheusResponse,PrometheusResponse>> = mutableListOf()

                val dataList: MutableList<Pair<String,PrometheusResponse>> = mutableListOf()

                intervalList.forEach { (stage, start, end) ->


                    if (stage in listOf("base","load")) {
                        val prometheusData = fetcher.fetchMetric(
                                start = start,
                                end = end,
                                stepSize = stepSize,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )


                        ioHandler.writeToCSVFile(
                                fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter}",
                                data = prometheusData.getResultAsList(),
                                columns = listOf("labels", "timestamp", "value")
                        )

                        if (workload != DEFAULT_WORKLOAD) {
                            val workloadData = fetcher.fetchMetric(
                                    start = start,
                                    end = end,
                                    stepSize = stepSize,
                                    query = SloConfigHandler.getQueryString(slo = slo)
                            )


                            ioHandler.writeToCSVFile(
                                    fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter}",
                                    data = workloadData.getResultAsList(),
                                    columns = listOf("labels", "timestamp", "value")
                            )


                            dataListWithQuery.add(Triple(stage,prometheusData,prometheusData))
                        } else {
                            dataList.add(Pair(stage,prometheusData))

                        }

                    }
                    if (workload != DEFAULT_WORKLOAD) {
                        totalDataListWithQuery.add(Pair(dataListWithQuery[0], dataListWithQuery[1]))
                    } else {
                        totalDataList.add(Pair(dataList[0],dataList[1]))
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

            if (workload != DEFAULT_WORKLOAD) {

                return sloChecker.evaluate(totalDataListWithQuery)
            } else {
                return sloChecker.evaluate(totalDataList, load)

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
    fun analyze(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
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





    /**
     *  Analyses an experiment via prometheus data.
     *  First fetches data from prometheus, then documents them and afterwards evaluate it via a [slo].
     *  @param load of the experiment.
     *  @param resource of the experiment.
     *  @param executionIntervals list of start and end points of experiments
     *  @return true if the experiment succeeded.
     */
    fun collect(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>) {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"





            val stepSize = slo.properties["promQLStepSeconds"]?.toLong()?.let { Duration.ofSeconds(it) } ?: DEFAULT_STEP_SIZE

            val stages = slo.properties["stages"]?.lowercase()?.split("+") ?: DEFAULT_STAGE


            executionIntervals.forEach { intervalList ->
                intervalList.forEach { (stage, start, end) ->



                    if (stage in listOf("base", "idle", "load") && stages.contains(stage)) {

                        // ADAPT FETCH METRIC SO THAT ALL PODS/CONTAINERS ARE PROVIDED FOR ANALYSIS
                        val prometheusData = fetcher.fetchMetric(
                                start = start,
                                end = end,
                                stepSize = stepSize,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )

                        ioHandler.writeToCSVFile(
                                fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter}",
                                data = prometheusData.getAllResultAsList(),
                                columns = listOf("labels", "timestamp", "value")
                        )
                    }
                }
                repetitionCounter++
            }




        } catch (e: Exception) {
            throw EvaluationFailedException("Collection failed for resource '$resource' and load '$load ", e)
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


