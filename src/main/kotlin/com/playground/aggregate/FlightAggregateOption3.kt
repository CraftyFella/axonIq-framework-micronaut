package com.playground.aggregate

import com.playground.FlightCommand
import com.playground.FlightEvent
import com.playground.FlightState
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy

interface Decider<TState, TCommand, TEvent> {
    fun decide(state: TState, command: TCommand): List<TEvent>
    fun evolve(state: TState, event: TEvent): TState
    fun initialState(): TState
    fun streamId(event: TEvent): String
}

class FlightDecider2 : Decider<FlightState, FlightCommand, FlightEvent> {
    override fun decide(state: FlightState, command: FlightCommand): List<FlightEvent> {
        return when (command) {
            is FlightCommand.ScheduleFlightCommand -> handle(state, command)
            is FlightCommand.DelayFlightCommand -> handle(state, command)
            is FlightCommand.CancelFlightCommand -> handle(state, command)
        }
    }

    override fun evolve(state: FlightState, event: FlightEvent): FlightState {
        return state.evolve(event)
    }

    override fun initialState(): FlightState {
        return FlightState.Empty
    }

    override fun streamId(event: FlightEvent): String {
        return event.flightId
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

abstract class DeciderAggregate2<TState, TCommand, TEvent, TSelf : DeciderAggregate2<TState, TCommand, TEvent, TSelf>> {
    protected abstract val decider: Decider<TState, TCommand, TEvent>
    var state: TState? = null

    @AggregateIdentifier
    var streamId: String? = null

    // Abstract methods that subclasses must implement
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    protected abstract fun handle(command: TCommand): String

    @EventSourcingHandler
    protected abstract fun on(event: TEvent)

    // Helper method called by concrete command handlers
    protected fun processCommand(command: TCommand): String {
        if (state == null) {
            state = decider.initialState()
        }
        val events = decider.decide(state!!, command)
        events.forEach { event ->
            AggregateLifecycle.apply(event)
        }
        return events.toString()
    }

    // Generic event handler
    protected fun handleEvent(event: TEvent) {
        if (streamId == null) {
            state = decider.initialState()
            streamId = decider.streamId(event)
        }
        this.state = decider.evolve(state!!, event)
    }

    @EventHandler
    fun applySnapshot(event: TSelf) {
        this.streamId = event.streamId
        this.state = event.state
    }
}

class FlightAggregateOption3 : DeciderAggregate2<FlightState, FlightCommand, FlightEvent, FlightAggregateOption3>() {
    override val decider: Decider<FlightState, FlightCommand, FlightEvent> = FlightDecider2()

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    override fun handle(command: FlightCommand): String {
        return processCommand(command)
    }

    @EventSourcingHandler
    override fun on(event: FlightEvent) {
        handleEvent(event)
    }
}