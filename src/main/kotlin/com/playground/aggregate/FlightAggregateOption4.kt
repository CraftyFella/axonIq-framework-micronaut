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

abstract class DeciderAggregate<TState, TCommand, TEvent> {
    protected abstract var state: TState
    @AggregateIdentifier
    var aggregateId: String? = null

    // Force subclasses to implement the decide function
    protected abstract fun decide(state: TState, command: TCommand): List<TEvent>
    protected abstract fun extractAggregateId(event: TEvent): String
    protected abstract fun evolveState(currentState: TState, event: TEvent): TState

    // Helper method called by concrete command handlers
    protected fun processCommand(command: TCommand): String {
        val events = decide(state, command)
        events.forEach { event ->
            AggregateLifecycle.apply(event)
        }
        return events.toString()
    }

    // Generic event handler - will be implemented in subclass with concrete type
    protected fun handleEvent(event: TEvent) {
        if (aggregateId == null) {
            aggregateId = extractAggregateId(event)
        }
        this.state = evolveState(state, event)
    }
}

class FlightAggregateOption4 : DeciderAggregate<FlightState, FlightCommand, FlightEvent>() {
    override var state: FlightState = FlightState.Empty

    // Concrete command handler method that the framework can find
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: FlightCommand): String {
        return processCommand(command)
    }

    // Concrete event handlers for each event type
    @EventSourcingHandler
    fun on(event: FlightEvent) {
        handleEvent(event)
    }

    override fun decide(state: FlightState, command: FlightCommand): List<FlightEvent> {
        return when (command) {
            is FlightCommand.ScheduleFlightCommand -> handle(state, command)
            is FlightCommand.DelayFlightCommand -> handle(state, command)
            is FlightCommand.CancelFlightCommand -> handle(state, command)
        }
    }

    override fun extractAggregateId(event: FlightEvent): String {
        return event.flightId
    }

    override fun evolveState(currentState: FlightState, event: FlightEvent): FlightState {
        return currentState.evolve(event)
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
}