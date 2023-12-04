package rocks.theodolite.kubernetes.slo

import DefaultEfficiencySloJson
import EfficiencySloJson
import SloJson
import mu.KotlinLogging
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration


/**
 * [SloChecker] that uses an external source for the concrete evaluation.
 * @param externalSlopeURL The url under which the external evaluation can be reached.
 * @param metadata metadata passed to the external SLO checker.
 */
class ExternalSloChecker(
    val externalSlopeURL: String,
    val metadata: Map<String, Any>
) : SloChecker {

    private val RETRIES = 2
    private val TIMEOUT = Duration.ofSeconds(60)

    private val logger = KotlinLogging.logger {}

    /**
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluate(fetchedData: List<PrometheusResponse>): Boolean {
        var counter = 0
        val data = SloJson(
            results = fetchedData.map { it.data?.result ?: listOf() },
            metadata = metadata
        ).toJson()

        while (counter < RETRIES) {
            val request = HttpRequest.newBuilder()
                    .uri(URI.create(externalSlopeURL))
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(TIMEOUT)
                    .build()
            val response = HttpClient.newBuilder()
                    .build()
                    .send(request, BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                counter++
                logger.error { "Received status code ${response.statusCode()} for request to $externalSlopeURL." }
            } else {
                val booleanResult = response.body().toBoolean()
                logger.info { "SLO checker result is: $booleanResult." }
                return booleanResult
            }
        }

        throw ConnectException("Could not reach external SLO checker at $externalSlopeURL.")
    }


    /**
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluate(fetchedData: List<Pair<Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>>>): Boolean {
        var counter = 0

//        fetchedData.map { Pair(Triple(it.first.first, it.first.second.data?.result ?: listOf(), it.first.third.data?.result ?: listOf()) ,Triple(it.second.first, it.second.second.data?.result ?: listOf(), it.second.third.data?.result ?: listOf())) }

//        val data = SloJson(
//                results = fetchedData.map { it.data?.result ?: listOf() },
//                metadata = metadata
//        ).toJson()

        val data = EfficiencySloJson(
                results = fetchedData.map {
                    Pair(Triple(it.first.first, it.first.second.data?.result ?: listOf(), it.first.third.data?.result ?: listOf()) ,Triple(it.second.first, it.second.second.data?.result ?: listOf(), it.second.third.data?.result ?: listOf()))
                                          },
                metadata = metadata
        ).toJson()


        while (counter < RETRIES) {
            val request = HttpRequest.newBuilder()
                    .uri(URI.create(externalSlopeURL))
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(TIMEOUT)
                    .build()
            val response = HttpClient.newBuilder()
                    .build()
                    .send(request, BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                counter++
                logger.error { "Received status code ${response.statusCode()} for request to $externalSlopeURL." }
            } else {
                val booleanResult = response.body().toBoolean()
                logger.info { "SLO checker result is: $booleanResult." }
                return booleanResult
            }
        }

        throw ConnectException("Could not reach external SLO checker at $externalSlopeURL.")
    }



    /**
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluate(fetchedData: List<Pair<Pair<String,PrometheusResponse>,Pair<String,PrometheusResponse>>>, load: Int): Boolean {
        var counter = 0
        val data = DefaultEfficiencySloJson(
                results = Pair(fetchedData.map { Pair(Pair(it.first.first,it.first.second.data?.result ?: listOf()), Pair(it.second.first, it.second.second.data?.result ?: listOf()))}, load),
                metadata = metadata
        ).toJson()

        while (counter < RETRIES) {
            val request = HttpRequest.newBuilder()
                    .uri(URI.create(externalSlopeURL))
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(TIMEOUT)
                    .build()
            val response = HttpClient.newBuilder()
                    .build()
                    .send(request, BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                counter++
                logger.error { "Received status code ${response.statusCode()} for request to $externalSlopeURL." }
            } else {
                val booleanResult = response.body().toBoolean()
                logger.info { "SLO checker result is: $booleanResult." }
                return booleanResult
            }
        }

        throw ConnectException("Could not reach external SLO checker at $externalSlopeURL.")
    }
}
