package rocks.theodolite.kubernetes.slo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.quarkus.runtime.annotations.RegisterForReflection
import java.util.*

/**
 * This class corresponds to the JSON response format of a Loki
 * query.
 */
@RegisterForReflection
data class LokiResponse(
        /**
         * Indicates whether the query was successful.
         */
        var status: String? = null,
        /**
         * The data section of the query result contains the information about the resultType and the results themselves.
         */
        var data: LokiData? = null
) {

    /**
     * Return the data of the LokiResponse as a [List] of [List]s of [String]s
     * The format of the returned list is similar to Prometheus: `[[ label, timestamp, value ], [ label, timestamp, value ], ... ]`
     */
    @JsonIgnore
    fun getResultAsList(): List<List<String>> {
        val result = mutableListOf<List<String>>()

        data?.result?.forEach { lokiResult ->
            val label = lokiResult.stream?.toString() ?: ""
            lokiResult.values?.forEach { value ->
                val valueList = value as List<*>
                val timestamp = valueList[0].toString()
                val resultValue = valueList[1].toString()
                result.add(listOf(label, timestamp, resultValue))
            }
        }
        return Collections.unmodifiableList(result)
    }
}

/**
 * Description of Loki data.
 */
@RegisterForReflection
data class LokiData(
        var resultType: String? = null,
        var result: List<LokiResult>? = null
)

/**
 * LokiResult corresponds to the result format of a Loki query.
 */
@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore fields not mapped
data class LokiResult(
        var stream: Map<String, String>? = null,
        var values: List<List<String>>? = null
)
