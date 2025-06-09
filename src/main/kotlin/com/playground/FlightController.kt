package com.playground

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.GenericMessage
import org.axonframework.messaging.MetaData
import kotlin.random.Random

@Controller("/flight")
@Produces(MediaType.TEXT_HTML)
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

	private fun <T> sendCommand(command: T): String {
		val commandMessage = GenericCommandMessage(GenericMessage<T?>(command, MetaData.emptyInstance()), FlightCommand::class.java.name)
		val result: Any = commandGateway.sendAndWait(commandMessage)
		return result.toString()
	}

	@Get("{flightId}/schedule")
	fun flight(flightId: String): String {
		val origin = randomAirport()
		val destination = randomAirport().let {
			if (it == origin) airports.first { airport -> airport != origin } else it
		}
		val flightNumber = randomFlightNumber()

		val command = FlightCommand.ScheduleFlightCommand(
			flightId = flightId,
			flightNumber = flightNumber,
			origin = origin,
			destination = destination
		)
		return sendCommand(command)
	}

	@Get("{flightId}/delay/")
	fun delay(flightId: String): String {
		val reason = delayReasons[Random.nextInt(delayReasons.size)]
		val command = FlightCommand.DelayFlightCommand(
			flightId = flightId,
			reason = reason
		)
		return sendCommand(command)
	}

	@Get("{flightId}/cancel/")
	fun cancel(flightId: String): String {
		val reason = cancelReasons[Random.nextInt(cancelReasons.size)]
		val command = FlightCommand.CancelFlightCommand(
			flightId = flightId,
			reason = reason
		)
		return sendCommand(command)
	}
}