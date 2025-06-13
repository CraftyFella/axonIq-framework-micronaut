package com.playground

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import org.awaitility.Awaitility
import org.junit.jupiter.api.Assertions
import java.time.Duration
import java.util.UUID

class FlightApiDsl(private val client: HttpClient) {
    // Flight creation
    fun scheduleFlight(
        flightId: String = randomFlightId(),
        flightNumber: String? = null,
        origin: String? = null,
        destination: String? = null
    ): FlightDetailsResponse {
        val request = ScheduleFlightRequest(flightId, flightNumber, origin, destination)
        val response = client.toBlocking().exchange(
            HttpRequest.POST("/flights", request),
            FlightDetailsResponse::class.java
        )
        return response.body()
    }

    // Flight modification
    fun delayFlight(flightId: String, reason: String? = null): DelayFlightResponse {
        val request = DelayFlightRequest(reason)
        val response = client.toBlocking().exchange(
            HttpRequest.PATCH("/flights/$flightId/delay", request),
            DelayFlightResponse::class.java
        )
        return response.body()
    }

    fun cancelFlight(flightId: String, reason: String? = null): CancelFlightResponse {
        val request = CancelFlightRequest(reason)
        val response = client.toBlocking().exchange(
            HttpRequest.PATCH("/flights/$flightId/cancel", request),
            CancelFlightResponse::class.java
        )
        return response.body()
    }

    // Flight queries
    fun getFlightDetails(flightId: String): FlightDetailsResponse {
        return client.toBlocking().retrieve(
            HttpRequest.GET<FlightDetailsResponse>("/flights/$flightId"),
            FlightDetailsResponse::class.java
        )
    }

    fun getAllFlights(): FlightsListResponse {
        return client.toBlocking().retrieve(
            HttpRequest.GET<FlightsListResponse>("/flights"),
            FlightsListResponse::class.java
        )
    }

    fun getFlightsByOrigin(origin: String): FlightsListResponse {
        return client.toBlocking().exchange(
            HttpRequest.GET<FlightsListResponse>("/flights/by-origin/$origin"),
            FlightsListResponse::class.java
        ).body()
    }

    fun getFlightsByDestination(destination: String): FlightsListResponse {
        return client.toBlocking().exchange(
            HttpRequest.GET<FlightsListResponse>("/flights/by-destination/$destination"),
            FlightsListResponse::class.java
        ).body()
    }

    // Utility for generating random flight IDs
    private fun randomFlightId(): String = UUID.randomUUID().toString()

    // Awaiting functionality
    fun awaitFlightByOrigin(origin: String, flightId: String, timeout: Duration = Duration.ofSeconds(5)) {
        Awaitility.await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                val flights = getFlightsByOrigin(origin).flights
                Assertions.assertTrue(flights.any { it == flightId },
                    "Flight should appear in the origin list")
            }
    }

    // Add this to the FlightApiDsl class
    fun awaitFlightByDestination(destination: String, flightId: String, timeout: Duration = Duration.ofSeconds(5)) {
        Awaitility.await()
            .atMost(timeout)
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted {
                val flights = getFlightsByDestination(destination).flights
                Assertions.assertTrue(flights.any { it == flightId },
                    "Flight should appear in the destination list")
            }
    }
}

// Extension function to create the DSL
fun HttpClient.flights() = FlightApiDsl(this)