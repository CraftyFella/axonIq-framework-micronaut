package com.playground.aggregate

import com.playground.FlightCommand
import com.playground.FlightEvent
import com.playground.FlightState
import com.playground.InjectableThing
import jakarta.inject.Inject
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy

// Option 1. see Readme.md for details
class `Option1.kt`() {

    @Inject
    @Transient
    private lateinit var thing: Thing

    var state: FlightState = FlightState.Companion.Empty
    @AggregateIdentifier
    var aggregateId: String? = null

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: FlightCommand.ScheduleFlightCommand): String {
        if (state is FlightState.EmptyFlight) {
            AggregateLifecycle.apply(FlightEvent.FlightScheduledEvent(command.flightId, command.origin, command.destination))
            return "Flight scheduled with id: ${command.flightId}"
        } else {
            return "Flight scheduled with id: ${command.flightId} again"
        }
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.NEVER)
    fun handle(command: FlightCommand.CancelFlightCommand): String {
        if (state is FlightState.CancelledFlight) {
            throw IllegalStateException("Flight already cancelled")
        }
        AggregateLifecycle.apply(FlightEvent.FlightCancelledEvent(command.flightId, command.reason))
        return "Flight cancelled with id: ${command.flightId}"
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.NEVER)
    fun handle(command: FlightCommand.DelayFlightCommand): String {
        if (state is FlightState.CancelledFlight) {
            throw IllegalStateException("Flight already cancelled")
        }
        AggregateLifecycle.apply(FlightEvent.FlightDelayedEvent(command.flightId, command.reason))
        return "Flight delayed with id: ${command.flightId}"
    }

    @EventSourcingHandler
    fun on(event: FlightEvent) {
        this.aggregateId = event.flightId
        this.state = this.state.evolve(event)
    }

}