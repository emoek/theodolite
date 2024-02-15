package rocks.theodolite.kubernetes.slo

import EfficiencySloJson
import LogAndStageBasedSloJson
import LogEfficiencySloJson
import SloJson
import StageBasedSloJson
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
        logger.info { "Possible Efficiency slo types: None" }
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
     * For: StageBased + WM
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluateStageBased(fetchedData: List<Triple<Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>,Triple<String,PrometheusResponse,PrometheusResponse>>>, load: Int): Boolean {
        var counter = 0
        logger.info { "Possible Efficiency slo types: 5, 6, 7, 8, 9, 10, 11, 12, 14" }




        val data = StageBasedSloJson(
                results = Pair(fetchedData.map {
                    Triple(Triple(it.first.first,it.first.second.data?.result ?: listOf(),it.first.third.data?.result ?: listOf()),Triple(it.second.first,it.second.second.data?.result ?: listOf(),it.second.third.data?.result ?: listOf()),Triple(it.third.first,it.third.second.data?.result ?: listOf(),it.third.third.data?.result ?: listOf()))
                                          }, load),
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
     * For: StageBased + WL
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluateLogAndStageBased(fetchedData: List<Triple<Triple<String,PrometheusResponse,LokiResponse>,Triple<String,PrometheusResponse,LokiResponse>,Triple<String,PrometheusResponse,LokiResponse>>>, load: Int): Boolean {
        var counter = 0
        logger.info { "Possible Efficiency slo types: 1, 2, 3, 4, 9, 10, 11, 12, 13" }



        val data = LogAndStageBasedSloJson(
                results = Pair(fetchedData.map {
                    Triple(Triple(it.first.first,it.first.second.data?.result ?: listOf(),it.first.third.data?.result ?: listOf()),Triple(it.second.first,it.second.second.data?.result ?: listOf(),it.second.third.data?.result ?: listOf()),Triple(it.third.first,it.third.second.data?.result ?: listOf(),it.third.third.data?.result ?: listOf()))
                }, load),
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
     * For: WL
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluateLogEfficiency(fetchedData: Pair<List<PrometheusResponse>, List<LokiResponse>>, load: Int): Boolean {
        var counter = 0

        logger.info { "Possible Efficiency slo types: 4, 10, 13" }


        val data = LogEfficiencySloJson(
                results = Pair(Pair(fetchedData.first.map { it.data?.result ?: listOf() }, fetchedData.second.map { it.data?.result ?: listOf() }),load),
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
     * For: WM
     * Evaluates an experiment using an external service.
     * Will try to reach the external service until success or [RETRIES] times.
     * Each request will time out after [TIMEOUT].
     *
     * @param fetchedData that should be evaluated
     * @return true if the experiment was successful (the threshold was not exceeded).
     * @throws ConnectException if the external service could not be reached.
     */
    override fun evaluateEfficiency(fetchedData: Pair<List<PrometheusResponse>, List<PrometheusResponse>>, load: Int): Boolean {
        var counter = 0

        logger.info { "Possible Efficiency slo types: 8, 10, 14" }



        val data = EfficiencySloJson(
                results = Pair(Pair(fetchedData.first.map { it.data?.result ?: listOf() }, fetchedData.second.map { it.data?.result ?: listOf() }),load),
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
