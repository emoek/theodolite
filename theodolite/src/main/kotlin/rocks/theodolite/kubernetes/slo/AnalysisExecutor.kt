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
//    fun analyze(load: Int, resource: Int, executionIntervals: List<List<Triple<String,Instant, Instant>>>): Boolean {
//        var repetitionCounter = 1
//
//
//
//        try {
//            val ioHandler = IOHandler()
//            val resultsFolder = ioHandler.getResultFolderURL()
//            val fileURL = "${resultsFolder}exp${executionId}_${load}_${resource}_${slo.sloType.toSlug()}"
//
//            var parts = slo.name.split("-")
//
//            val isCollect = parts.firstOrNull() == "collect"
//            val nameIndex = if (isCollect) 1 else 0
//            val name = parts.getOrNull(nameIndex) ?: throw IllegalArgumentException("Invalid or no username")
//
//            val stagePart = parts.getOrNull(nameIndex + 1)
//            val stages = stagePart?.split("+") ?: emptyList()
//
//            val prometheusData: MutableList<PrometheusResponse> = mutableListOf()
//
//
//
//            for (intervalList in executionIntervals) {
//
////            executionIntervals.forEach { intervalList ->
////                if (slo.name.startsWith("collect")) {
////                    val stuff = intervalList.map { triple ->
////                        // Your transformation logic here
////                        // For example, process each triple and return a String
////
////
////
////                    }
////                }
//
//
//
//
//                val data: MutableList<String>
//
//
//                if (slo.name.startsWith("collect")) {
//
//
//
//                    for (triple in intervalList) {
//                        val (stage, start, end) = triple
//
//
//                        if (stages.contains("base")) {
//                            val baseData = fetcher.fetchMetric(start,end,query = SloConfigHandler.getQueryString(slo = slo))
//                            ioHandler.writeToCSVFile( fileURL = "${fileURL}_${slo.name}_${repetitionCounter}_base",
//                                    data = baseData.getResultAsList(),
//                                    columns = listOf("labels", "timestamp", "value"))
//
//
//                        }
//
//                        if (stages.contains("idle")) {
//                            val idleData = fetcher.fetchMetric(start,end,query = SloConfigHandler.getQueryString(slo = slo))
//                            ioHandler.writeToCSVFile( fileURL = "${fileURL}_${slo.name}_${repetitionCounter}_idle",
//                                    data = idleData.getResultAsList(),
//                                    columns = listOf("labels", "timestamp", "value"))
//
//                        }
//
//                        if (stages.contains("load")) {
//                            val loadData = fetcher.fetchMetric(start,end,query = SloConfigHandler.getQueryString(slo = slo))
//                            ioHandler.writeToCSVFile( fileURL = "${fileURL}_${slo.name}_${repetitionCounter}_load",
//                                    data = loadData.getResultAsList(),
//                                    columns = listOf("labels", "timestamp", "value"))
//                        }
//
////                        ioHandler.writeToCSVFile(fileURL="${fileURL}_${slo.name}_${repetitionCounter++}",
////                                data = data.getResultAsList(),
////                                columns = listOf("labels", "timestamp", "value"))
//
//
//
////                        if(slo.name.endsWith("base")) {
////
////                        } else if (slo.name.endsWith("idle")) {
////
////                        } else {
////
////                        }
//
//
//
//                    }
//
//                } else {
//                    val loadTriples = intervalList.filter { triple -> triple.first == "load" }.first()
//
//                    val loadData = fetcher.fetchMetric(loadTriples.second,loadTriples.third,query = SloConfigHandler.getQueryString(slo = slo))
//                    ioHandler.writeToCSVFile( fileURL = "${fileURL}_${slo.name}_${repetitionCounter}_load",
//                            data = loadData.getResultAsList(),
//                            columns = listOf("labels", "timestamp", "value"))
//
//                    prometheusData.add(loadData)
//
////                    val sloChecker = SloCheckerFactory().create(
////                            sloType = slo.sloType,
////                            properties = slo.properties,
////                            load = load,
////                            resources = resource
////                    )
////
////                    return sloChecker.evaluate()
//
//                }
//                repetitionCounter++
//
//            }
//
//
////            val prometheusData = executionIntervals
////                    .map { interval ->
////                        fetcher.fetchMetric(
////                                start = interval.first,
////                                end = interval.second,
////                                query = SloConfigHandler.getQueryString(slo = slo)
////                        )
////                    }
//
////            prometheusData.forEach{ data ->
////                ioHandler.writeToCSVFile(
////                        fileURL = "${fileURL}_${slo.name}_${repetitionCounter++}",
////                        data = data.getResultAsList(),
////                        columns = listOf("labels", "timestamp", "value")
////                )
////            }
//            if (!slo.name.startsWith("collect")) {
//                val sloChecker = SloCheckerFactory().create(
//                        sloType = slo.sloType,
//                        properties = slo.properties,
//                        load = load,
//                        resources = resource
//                )
//
//                return sloChecker.evaluate(prometheusData)
//
//            } else {
//                return true
//            }
//
//        } catch (e: Exception) {
//            throw EvaluationFailedException("Evaluation failed for resource '$resource' and load '$load ", e)
//        }
//    }

















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


            val parts = slo.name.split("-")

//            val isCollect = parts.firstOrNull() == "collect"
//            val nameIndex = if (isCollect) 1 else 0
            val nameIndex = 1
            val name = parts.getOrNull(nameIndex) ?: throw IllegalArgumentException("Invalid or no username")

            val stagePart = parts.getOrNull(nameIndex + 1)
            val stages = stagePart?.split("+") ?: emptyList()


            val prometheusDataList: MutableList<PrometheusResponse> = mutableListOf()
            executionIntervals.forEach { intervalList ->
                intervalList.forEach { (stage, start, end) ->
                    // Check stage and process accordingly


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
                                fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter++}",
                                data = prometheusData.getResultAsList(),
                                columns = listOf("labels", "timestamp", "value")
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
                    // Check stage and process accordingly



                    if (stage in listOf("base", "idle", "load") && stage in stages) {

                        // ADAPT FETCH METRIC SO THAT ALL PODS/CONTAINERS ARE PROVIDED FOR ANALYSIS
                        val prometheusData = fetcher.fetchMetric(
                                start = start,
                                end = end,
                                query = SloConfigHandler.getQueryString(slo = slo)
                        )

                        // maybe replace slo.name with name
                        ioHandler.writeToCSVFile(
                                fileURL = "${fileURL}_${slo.name}_${stage}_${repetitionCounter++}",
                                data = prometheusData.getResultAsList(),
                                columns = listOf("labels", "timestamp", "value")
                        )
                    }
                }
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
