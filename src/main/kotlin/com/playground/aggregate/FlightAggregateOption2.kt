package com.playground.aggregate

import com.playground.FlightCommand
import com.playground.FlightEvent
import com.playground.FlightState
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy
import kotlin.collections.forEach

class FlightAggregateOption2() {
    var state: FlightState = FlightState.Companion.Empty
    @AggregateIdentifier
    var aggregateId: String? = null


    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: FlightCommand): String {
        val events = decide(state, command)
        events.forEach { event ->
            AggregateLifecycle.apply(event)
        }
        return events.toString()
    }

    private fun decide(state: FlightState, command: FlightCommand): List<FlightEvent> {
        return when (command) {
            is FlightCommand.ScheduleFlightCommand -> handle(state, command)
            is FlightCommand.DelayFlightCommand -> handle(state, command)
            is FlightCommand.CancelFlightCommand -> handle(state, command)
        }
    }

    private fun handle(
        state: FlightState,
        command: FlightCommand.ScheduleFlightCommand
    ): List<FlightEvent> {
        return if (state is FlightState.EmptyFlight) {
            listOf(FlightEvent.FlightScheduledEvent(command.flightId, command.origin, command.destination))
        } else {
            listOf()
        }
    }

    private fun handle(
        state: FlightState,
        command: FlightCommand.DelayFlightCommand
    ): List<FlightEvent> {
        if (state is FlightState.CancelledFlight) {
            throw IllegalStateException("Flight already cancelled")
        }
        return listOf(FlightEvent.FlightDelayedEvent(command.flightId, command.reason))
    }

    private fun handle(
        state: FlightState,
        command: FlightCommand.CancelFlightCommand
    ): List<FlightEvent> {
        if (state is FlightState.CancelledFlight) {
            throw IllegalStateException("Flight already cancelled")
        }
        return listOf(FlightEvent.FlightCancelledEvent(command.flightId, command.reason))
    }

    @EventSourcingHandler
    fun on(event: FlightEvent) {
        this.aggregateId = event.flightId
        this.state = this.state.evolve(event)
    }
}