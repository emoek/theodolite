import com.fasterxml.jackson.databind.ObjectMapper
import rocks.theodolite.kubernetes.slo.LokiResponse
import rocks.theodolite.kubernetes.slo.LokiResult
import rocks.theodolite.kubernetes.slo.PromResult
import rocks.theodolite.kubernetes.slo.PrometheusResponse

abstract class AbstractSloJson {
    abstract val results: Any
    abstract var metadata: Map<String, Any>

    fun toJson(): String {
        return ObjectMapper().writeValueAsString(
                mapOf(
                        "results" to this.results,
                        "metadata" to this.metadata
                )
        )
    }
}

class SloJson(
        override val results: List<List<PromResult>>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()

class StageBasedSloJson(
        override val results: Pair<List<Triple<Triple<String,List<PromResult>,List<PromResult>>,Triple<String,List<PromResult>,List<PromResult>>,Triple<String,List<PromResult>,List<PromResult>>>>, Int>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()


class LogAndStageBasedSloJson(
        override val results: Pair<List<Triple<Triple<String,List<PromResult>,List<LokiResult>>,Triple<String,List<PromResult>,List<LokiResult>>,Triple<String,List<PromResult>,List<LokiResult>>>>, Int>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()

class EfficiencySloJson(
        override val results: Pair<Pair<List<List<PromResult>>, List<List<PromResult>>>, Int>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()


class LogEfficiencySloJson(
        override val results: Pair<Pair<List<List<PromResult>>, List<List<LokiResult>>>, Int>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()

