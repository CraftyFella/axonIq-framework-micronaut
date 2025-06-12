package com.playground
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.collections.get

@MicronautTest
class AxonCartMongoTest {

    @Inject
    lateinit var application: EmbeddedApplication<*>

    @Test
    fun testItWorks() {
        Assertions.assertTrue(application.isRunning)
    }

}

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlightApiTest: TestPropertyProvider {

    companion object {
        lateinit var postgres: PostgreSQLContainer<*>
    }

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun `when scheduling a flight it appears in get details`() {
        val flightId = randomFlightId()
        val scheduleRequest = HttpRequest.POST("/flights", mapOf("flightId" to flightId))
        val scheduleResponse = client.toBlocking().exchange(scheduleRequest, Map::class.java)
        assertEquals(201, scheduleResponse.status.code)

        val detailsRequest = HttpRequest.GET<Map<String, Any>>("/flights/$flightId")
        val detailsResponse = client.toBlocking().retrieve(detailsRequest, Map::class.java)
        assertEquals(flightId, detailsResponse["flightId"])
        assertEquals("SCHEDULED", detailsResponse["status"])
    }

    private fun randomFlightId(): String {
        return UUID.randomUUID().toString()
    }

    @Test
    fun `when cancelling a flight it appears as cancelled`() {
        val flightId = randomFlightId()
        client.toBlocking().exchange(HttpRequest.POST("/flights", mapOf("flightId" to flightId)), Map::class.java)
        client.toBlocking().exchange(HttpRequest.PATCH("/flights/$flightId/cancel", mapOf("reason" to "Weather")), Map::class.java)

        val details = client.toBlocking().retrieve(HttpRequest.GET<Map<String, Any>>("/flights/$flightId"), Map::class.java)
        assertEquals("CANCELLED", details["status"])
        assertEquals("Weather", details["cancelReason"])
    }

    @Test
    fun `when delaying a flight it appears as delayed with reason`() {
        val flightId = randomFlightId()
        client.toBlocking().exchange(HttpRequest.POST("/flights", mapOf("flightId" to flightId)), Map::class.java)
        client.toBlocking().exchange(HttpRequest.PATCH("/flights/$flightId/delay", mapOf("reason" to "Technical")), Map::class.java)

        val details = client.toBlocking().retrieve(HttpRequest.GET<Map<String, Any>>("/flights/$flightId"), Map::class.java)
        assertEquals("DELAYED", details["status"])
        assertEquals("[Technical]", details["delayReasons"].toString())
    }

    override fun getProperties(): @NonNull Map<String?, String?>? {
        postgres = PostgreSQLContainer("postgres:15").apply {
            withDatabaseName("axon_eventstore")
            withUsername("postgres")
            withPassword("password")
            start()
        }
        return mapOf(
            "db.url" to postgres.jdbcUrl,
            "db.username" to postgres.username,
            "db.password" to postgres.password
        )
    }
}