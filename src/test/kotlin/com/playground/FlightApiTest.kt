package com.playground

import com.playground.library.DeadLetterQueueFactory
import com.playground.projections.ScheduledFlightsByDestinationProjection
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Duration
import java.util.*

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlightApiTest: TestPropertyProvider {

    companion object {
        lateinit var postgres: PostgreSQLContainer<*>
    }

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Inject
    lateinit var deadLetterQueueFactory: DeadLetterQueueFactory

    private lateinit var flightApi: FlightApiDsl

    @BeforeEach
    fun setup() {
        flightApi = client.flights()
    }

    @Test
    fun `when scheduling a flight with server generated values it appears in get details`() {
        // Arrange & Act - Schedule a flight with default values
        val scheduled = flightApi.scheduleFlight()
        val flightId = scheduled.flightId

        // Act - Get the flight details
        val details = flightApi.getFlightDetails(flightId)

        // Assert - Verify all expected fields in the response
        Assertions.assertEquals(flightId, details.flightId)
        Assertions.assertNotNull(details.flightNumber)
        Assertions.assertEquals("SCHEDULED", details.status)
        Assertions.assertNotNull(details.origin)
        Assertions.assertNotNull(details.destination)
        Assertions.assertNull(details.cancelReason)
        Assertions.assertTrue(details.delayReasons.isEmpty())
    }

    @Test
    fun `when scheduling a flight with client generated values it appears in get details`() {
        // Arrange & Act - Create and schedule a flight with specific fields
        val flightId = UUID.randomUUID().toString()
        val flightNumber = "FL1234"
        val origin = "JFK"
        val destination = "LAX"

        val scheduled = flightApi.scheduleFlight(
            flightId = flightId,
            flightNumber = flightNumber,
            origin = origin,
            destination = destination
        )

        // Act - Get the flight details
        val details = flightApi.getFlightDetails(flightId)

        // Assert - Verify all expected fields in the response
        Assertions.assertEquals(flightId, details.flightId)
        Assertions.assertEquals(flightNumber, details.flightNumber)
        Assertions.assertEquals(origin, details.origin)
        Assertions.assertEquals(destination, details.destination)
        Assertions.assertEquals("SCHEDULED", details.status)
        Assertions.assertTrue(details.delayReasons.isEmpty())
        Assertions.assertNull(details.cancelReason)
    }

    @Test
    fun `when cancelling a flight it appears as cancelled`() {
        // Arrange - Schedule a flight
        val scheduled = flightApi.scheduleFlight()

        // Act - Cancel the flight
        val cancelResponse = flightApi.cancelFlight(scheduled.flightId, "Weather")

        // Assert - Check the flight details
        val details = flightApi.getFlightDetails(scheduled.flightId)
        Assertions.assertEquals("CANCELLED", details.status)
        Assertions.assertEquals("Weather", details.cancelReason)
    }

    @Test
    fun `when delaying a flight it appears as delayed with reason`() {
        // Arrange - Schedule a flight
        val scheduled = flightApi.scheduleFlight()

        // Act - Delay the flight
        val delayResponse = flightApi.delayFlight(scheduled.flightId, "Technical")

        // Assert - Check the flight details
        val details = flightApi.getFlightDetails(scheduled.flightId)
        Assertions.assertEquals("DELAYED", details.status)
        Assertions.assertTrue(details.delayReasons.contains("Technical") ?: false)
    }

    @Test
    fun `when scheduling a flight it eventually appears in the flights by origin list`() {
        // Arrange - Create a flight with a specific origin
        val origin = "LHR"
        val scheduled = flightApi.scheduleFlight(origin = origin)

        // Assert - Wait for the flight to appear in the origin list
        flightApi.awaitFlightByOrigin(origin, scheduled.flightId)

        // Check a different origin returns empty list
        val otherOriginFlights = flightApi.getFlightsByOrigin("XXX")
        Assertions.assertTrue(otherOriginFlights.flights.isEmpty(),
            "Flights by origin XXX should be empty since we scheduled a flight with origin LHR")
    }

    @Test
    fun `when scheduling a flight it eventually appears in the flights by destination list`() {
        // Arrange - Create a flight with a specific destination
        val destination = "SYD"
        val scheduled = flightApi.scheduleFlight(destination = destination)

        // Assert - Wait for the flight to appear in the destination list
        flightApi.awaitFlightByDestination(destination, scheduled.flightId)

        // Check a different destination returns empty list
        val otherDestinationFlights = flightApi.getFlightsByDestination("XXX")
        Assertions.assertTrue(otherDestinationFlights.flights.isEmpty(),
            "Flights by destination XXX should be empty since we scheduled a flight with destination SYD")
    }

    @Test
    fun `when scheduling a flight with ID starting with bang- it ends up in DLQ and not in the projection`() {
        // Arrange - Create a flight with an ID that will cause exception
        val flightId = "bang-flight-${UUID.randomUUID()}"
        val destination = "DLQ-TEST"

        // Create a DLQ instance for the specific processing group
        val dlq = deadLetterQueueFactory.create(ScheduledFlightsByDestinationProjection.NAME)

        // Act - Schedule a flight that will cause an exception in the projection
        flightApi.scheduleFlight(
            flightId = flightId,
            destination = destination
        )

        // Assert - The flight details should still be available from the main query model
        val details = flightApi.getFlightDetails(flightId)
        Assertions.assertEquals(flightId, details.flightId)
        Assertions.assertEquals("SCHEDULED", details.status)

        // Wait for the event to appear in the DLQ
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                var foundInDlq = false
                dlq.deadLetters().forEach { sequence ->
                    sequence.forEach { deadLetter ->
                        val payload = deadLetter.message().payload
                        if (payload is FlightEvent.FlightScheduledEvent && payload.flightId == flightId) {
                            foundInDlq = true
                        }
                    }
                }
                Assertions.assertTrue(foundInDlq, "The flight event should be in the dead letter queue")
            }

        // Now check that the flight does NOT appear in the destination projection
        val destinationFlights = flightApi.getFlightsByDestination(destination)
        Assertions.assertFalse(destinationFlights.flights.any { it == flightId },
            "Flight with ID $flightId should not appear in destination projection due to exception")
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