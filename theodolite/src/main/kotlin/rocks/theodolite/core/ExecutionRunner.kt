package rocks.theodolite.core

import rocks.theodolite.core.strategies.Metric
import rocks.theodolite.core.strategies.searchstrategy.SearchStrategy


class ExecutionRunner(
    private val searchStrategy: SearchStrategy,
    private val resources: List<Int>, private val loads: List<Int>,
    private val metric: Metric, private val executionId: Int
) {

    /**
     * Run all experiments for given loads and resources.
     * Called by [rocks.theodolite.kubernetes.execution.TheodoliteExecutor] to run an Execution.
     */
    fun run() {

        val ioHandler = IOHandler()
        val resultsFolder = ioHandler.getResultFolderURL()

        try {
            searchStrategy.applySearchStrategyByMetric(loads, resources, metric)

        } finally {
            println("Results - Metric:"+searchStrategy.experimentRunner.results.metric)
            println("Results - Empty"+ searchStrategy.experimentRunner.results.isEmpty())
            println("Results:" + searchStrategy.experimentRunner.results.toString())
            ioHandler.writeToJSONFile(
                searchStrategy.experimentRunner.results,
                "${resultsFolder}exp${executionId}-result.json"
            )
            val res = searchStrategy.experimentRunner.results
            val resString = res.getExperimentResults().map { (key, value) -> listOf(key.first.toString(), key.second.toString(), value.toString()) }
            ioHandler.writeToCSVFile("${resultsFolder}exp${executionId}-resultOfExperiments.json", resString, listOf("Load", "Resource", "Result"))
            when (metric) {
                Metric.DEMAND ->
                    ioHandler.writeToCSVFile(
                        "${resultsFolder}exp${executionId}_demand",
                        calculateMetric(loads, searchStrategy.experimentRunner.results),
                        listOf("load", "resources")
                    )

                Metric.CAPACITY ->
                    ioHandler.writeToCSVFile(
                        "${resultsFolder}exp${executionId}_capacity",
                        calculateMetric(resources, searchStrategy.experimentRunner.results),
                        listOf("resource", "loads")
                    )

                Metric.EFFICIENCY ->
                    println("Efficiency metric")

            }
        }
    }

    private fun calculateMetric(xValues: List<Int>, results: Results): List<List<String>> {
        return xValues.map { listOf(it.toString(), results.getOptimalYValue(it).toString()) }
    }
}