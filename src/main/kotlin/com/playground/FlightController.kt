package com.playground

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import org.axonframework.commandhandling.gateway.CommandGateway

@Controller("/flight")
class FlightController(private val commandGateway: CommandGateway) {

	@Get("{flightId}/schedule")
	fun flight(flightId: String): String {
		val result: Any = commandGateway.sendAndWait(ScheduleFlightCommand(flightId))
		return result.toString()
	}

	@Get("{flightId}/delay/")
	fun delay(flightId: String): String {
		val result: Any = commandGateway.sendAndWait(DelayFlightCommand(flightId))
		return result.toString()
	}

	@Get("{flightId}/cancel/")
	fun cancel(flightId: String): String {
		val result: Any = commandGateway.sendAndWait(CancelFlightCommand(flightId))
		return result.toString()
	}
}