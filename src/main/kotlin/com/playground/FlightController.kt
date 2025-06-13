package com.playground

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.serde.annotation.Serdeable
import org.axonframework.queryhandling.QueryGateway
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

@Controller("/flights")
class FlightController(
	private val commandGateway: SumTypeCommandDispatcher,
	private val queryGateway: QueryGateway
) {

	private val airports = listOf("JFK", "LAX", "LHR", "CDG", "SYD", "DXB", "HND", "PEK")
	private val delayReasons = listOf(
		"Weather conditions",
		"Technical issues",
		"Air traffic congestion",
		"Crew scheduling",
		"Late arriving aircraft"
	)
	private val cancelReasons = listOf(
		"Severe weather conditions",
		"Mechanical failure",
		"Security concern",
		"Insufficient bookings",
		"Operational constraints"
	)

	private fun randomAirport() = airports[Random.nextInt(airports.size)]
	private fun randomFlightNumber() = "FL${Random.nextInt(1000, 9999)}"



	// Query endpoints
	@Get("/{flightId}")
	@Produces(MediaType.APPLICATION_JSON)
	fun getFlightDetails(flightId: String): CompletableFuture<FlightDetailsResponse> {
		return queryGateway.query(
			FlightQuery.GetFlightDetailsQuery(flightId),
			FlightDetailsResponse::class.java
		)
	}

	@Get
	@Produces(MediaType.APPLICATION_JSON)
	fun getAllFlights(): CompletableFuture<FlightsListResponse> {
		return queryGateway.query(
			FlightQuery.GetAllFlightsQuery(""),
			FlightsListResponse::class.java
		)
	}

	@Get("/by-destination/{destination}")
	@Produces(MediaType.APPLICATION_JSON)
	fun getFlightsByDestination(destination: String): CompletableFuture<FlightsListResponse> {
		return queryGateway.query(
			FlightQuery.FlightsByDestination(destination),
			FlightsListResponse::class.java
		)
	}

	@Get("/by-origin/{origin}")
	@Produces(MediaType.APPLICATION_JSON)
	fun getFlightsByOrigin(origin: String): CompletableFuture<FlightsListResponse> {
		return queryGateway.query(
			FlightQuery.FlightsByOrigin(origin),
			FlightsListResponse::class.java
		)
	}

	// Command endpoints - using more appropriate HTTP methods
	@Post
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	fun scheduleFlight(@Body request: ScheduleFlightRequest): HttpResponse<FlightDetailsResponse> {
		val flightId = request.flightId ?: java.util.UUID.randomUUID().toString()
		val origin = request.origin ?: randomAirport()
		val destination = request.destination ?: randomAirport().let {
			if (it == origin) airports.first { airport -> airport != origin } else it
		}
		val flightNumber = request.flightNumber ?: randomFlightNumber()

		val command = FlightCommand.ScheduleFlightCommand(
			flightId = flightId,
			flightNumber = flightNumber,
			origin = origin,
			destination = destination
		)
		commandGateway.sendCommandAsSumType(command, FlightCommand::class.java)
		val response = FlightDetailsResponse(
			flightId,
			flightNumber = flightNumber,
			origin = origin,
			destination = destination,
			status = "SCHEDULED"
		)
		return HttpResponse.created(response)
	}

	@Patch("/{flightId}/delay")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	fun delayFlight(flightId: String, @Body request: DelayFlightRequest): HttpResponse<DelayFlightResponse> {
		val reason = request.reason ?: delayReasons[Random.nextInt(delayReasons.size)]
		val command = FlightCommand.DelayFlightCommand(
			flightId = flightId,
			reason = reason
		)
		commandGateway.sendCommandAsSumType(command, FlightCommand::class.java)
		return HttpResponse.ok(DelayFlightResponse(
			flightId = flightId,
			delayReasons = listOf(reason)
		))
	}

	@Patch("/{flightId}/cancel")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	fun cancelFlight(flightId: String, @Body request: CancelFlightRequest): HttpResponse<CancelFlightResponse> {
		val reason = request.reason ?: cancelReasons[Random.nextInt(cancelReasons.size)]
		val command = FlightCommand.CancelFlightCommand(
			flightId = flightId,
			reason = reason
		)
		commandGateway.sendCommandAsSumType(command, FlightCommand::class.java)
		return HttpResponse.ok(CancelFlightResponse(
			flightId = flightId,
			cancelReason = reason
		))
	}
}

@Serdeable
data class ScheduleFlightRequest(
	val flightId: String? = null,
	val flightNumber: String? = null,
	val origin: String? = null,
	val destination: String? = null
)

@Serdeable
data class DelayFlightRequest(val reason: String? = null)

@Serdeable
data class CancelFlightRequest(val reason: String? = null)

@Serdeable
data class CancelFlightResponse(
	val flightId: String,
	val status: String = "CANCELLED",
	val cancelReason: String
)

@Serdeable
data class DelayFlightResponse(
	val flightId: String,
	val status: String = "DELAYED",
	val delayReasons: List<String>
)