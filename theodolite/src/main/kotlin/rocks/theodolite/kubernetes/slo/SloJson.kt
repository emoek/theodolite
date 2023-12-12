import com.fasterxml.jackson.databind.ObjectMapper
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

class EfficiencySloJson(
        override val results: List<Pair<Triple<String, List<PromResult>, List<PromResult>>, Triple<String, List<PromResult>, List<PromResult>>>>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()


class StageBasedSloJson(
        override val results: Pair<List<Triple<Triple<String,List<PromResult>,List<PromResult>>,Triple<String,List<PromResult>,List<PromResult>>,Triple<String,List<PromResult>,List<PromResult>>>>, Int>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()

class DefaultEfficiencySloJson(
        override val results: Pair<List<Pair<Pair<String, List<PromResult>>,Pair<String, List<PromResult>>>>, Int>,
        override var metadata: Map<String, Any>
) : AbstractSloJson()
