package rocks.theodolite.kubernetes.slo

/**
 * A SloChecker can be used to evaluate data from Prometheus.
 * @constructor Creates an empty SloChecker
 */
interface SloChecker {
    /**
     * Evaluates [fetchedData] and returns if the experiments were successful.
     *
     * @param fetchedData from Prometheus that will be evaluated.
     * @return true if experiments were successful. Otherwise, false.
     */
    fun evaluate(fetchedData: List<PrometheusResponse>): Boolean


    /**
     * Evaluates [fetchedData] and returns if the experiments were successful.
     *
     * @param fetchedData from Prometheus that will be evaluated.
     * @return true if experiments were successful. Otherwise, false.
     */
    fun evaluateEfficiencyQuery(fetchedData: List<Pair<Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>>>): Boolean


    /**
     * Evaluates [fetchedData] and returns if the experiments were successful.
     *
     * @param fetchedData from Prometheus that will be evaluated.
     * @return true if experiments were successful. Otherwise, false.
     */
    fun evaluateEfficiency(fetchedData: List<Pair<Pair<String,PrometheusResponse>,Pair<String,PrometheusResponse>>>, load: Int): Boolean
}
