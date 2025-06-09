package com.playground

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import org.axonframework.commandhandling.gateway.CommandGateway
import kotlin.random.Random

@Controller("/flight")
class FlightController(private val commandGateway: CommandGateway) {

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

	@Get("{flightId}/schedule")
	fun flight(flightId: String): String {
		val origin = randomAirport()
		val destination = randomAirport().let {
			if (it == origin) airports.first { airport -> airport != origin } else it
		}
		val flightNumber = randomFlightNumber()

		val result: Any = commandGateway.sendAndWait(
			FlightCommand.ScheduleFlightCommand(
				flightId = flightId,
				flightNumber = flightNumber,
				origin = origin,
				destination = destination
			)
		)
		return result.toString()
	}

	@Get("{flightId}/delay/")
	fun delay(flightId: String): String {
		val reason = delayReasons[Random.nextInt(delayReasons.size)]
		val result: Any = commandGateway.sendAndWait(
			FlightCommand.DelayFlightCommand(
				flightId = flightId,
				reason = reason
			)
		)
		return result.toString()
	}

	@Get("{flightId}/cancel/")
	fun cancel(flightId: String): String {
		val reason = cancelReasons[Random.nextInt(cancelReasons.size)]
		val result: Any = commandGateway.sendAndWait(
			FlightCommand.CancelFlightCommand(
				flightId = flightId,
				reason = reason
			)
		)
		return result.toString()
	}
}