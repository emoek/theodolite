package rocks.theodolite.kubernetes.slo

import rocks.theodolite.core.strategies.Metric
import rocks.theodolite.core.IOHandler
import rocks.theodolite.kubernetes.model.KubernetesBenchmark.Slo
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

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
    fun analyze(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>): Boolean {
        var repetitionCounter = 1

        try {
            val ioHandler = IOHandler()
            val resultsFolder = ioHandler.getResultFolderURL()
            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"


//            val parts = slo.name.split("-")
//
//
//            val nameIndex = 0
//            val name = parts.getOrNull(nameIndex) ?: throw IllegalArgumentException("Invalid or no username")
//
//            val stagePart = parts.getOrNull(nameIndex + 1)
//            val stages = stagePart?.split("+") ?: emptyList()


            val prometheusDataList: MutableList<PrometheusResponse> = mutableListOf()
            executionIntervals.forEach { intervalList ->
                intervalList.forEach { (stage, start, end) ->


                    // currently only load interval, maybe allow configuration by user over name?
                    if (stage in listOf("load")) {
                        val prometheusData = fetcher.fetchMetric(
                                start = start,
                                end = end,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )
                        prometheusDataList.add(prometheusData)

                        // maybe replace slo.name with name
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


            val parts = slo.name.split("-")

//            val isCollect = parts.firstOrNull() == "collect"
//            val nameIndex = if (isCollect) 1 else 0
            val nameIndex = 1
            val name = parts.getOrNull(nameIndex) ?: throw IllegalArgumentException("Invalid or no username")

            val stagePart = parts.getOrNull(nameIndex + 1)
            val stages = stagePart?.split("+") ?: emptyList()



            executionIntervals.forEach { intervalList ->
                intervalList.forEach { (stage, start, end) ->



                    if (stage in listOf("base", "idle", "load") && stage in stages) {

                        // ADAPT FETCH METRIC SO THAT ALL PODS/CONTAINERS ARE PROVIDED FOR ANALYSIS
                        val prometheusData = fetcher.fetchMetric(
                                start = start,
                                end = end,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )

                        // maybe replace slo.name with name
                        ioHandler.writeToCSVFile(
                                fileURL = "${fileURL}_collect_${name}_${stage}_${repetitionCounter}",
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
