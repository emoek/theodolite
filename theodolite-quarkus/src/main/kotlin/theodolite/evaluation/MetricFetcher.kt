package theodolite.evaluation

import khttp.get
import java.util.*


class MetricFetcher(private val prometheusURL: String) {

    fun fetchMetric(start: Long, end: Long, query: String): Any {

        val parameter = mapOf(
            "query" to query,
            "start" to toISODate(start),
            "end" to toISODate(end),
            "step" to "5s"
        )

        val response = get("$prometheusURL/api/v1/query_range", params = parameter)
        //TODO FIX: NOTHING RECEIVED
        return response.jsonObject.getJSONObject("data").getJSONArray("result")//.getJSONObject(0)["values"]
    }

    private fun toISODate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'")
        val date = Date(timestamp - (3600 * 1000))
        return sdf.format(date)
    }

    private fun parseValues(values: String): Any {
        // TODO("pars with gson")
        return ""
    }
}
