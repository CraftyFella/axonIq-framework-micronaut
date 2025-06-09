package com.playground

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.tracing.annotation.NewSpan
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy
import org.axonframework.modelling.command.TargetAggregateIdentifier

sealed interface FlightCommand {
    val flightId: String
    data class ScheduleFlightCommand(@TargetAggregateIdentifier override val flightId: String, val flightNumber: String, val origin: String, val destination: String) : FlightCommand
    data class DelayFlightCommand(@TargetAggregateIdentifier override val flightId: String, val reason: String): FlightCommand
    data class CancelFlightCommand(@TargetAggregateIdentifier override val flightId: String, val reason: String): FlightCommand
}

sealed interface FlightEvent {
    val flightId: String
    data class FlightScheduledEvent(override val flightId: String,  val origin: String, val destination: String) : FlightEvent
    data class FlightDelayedEvent(override val flightId: String, val reason: String) : FlightEvent
    data class FlightCancelledEvent(override val flightId: String, val reason: String) : FlightEvent
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FlightState.EmptyFlight::class, name = "empty"),
    JsonSubTypes.Type(value = FlightState.ScheduledFlight::class, name = "scheduled"),
    JsonSubTypes.Type(value = FlightState.DelayedFlight::class, name = "delayed"),
    JsonSubTypes.Type(value = FlightState.CancelledFlight::class, name = "cancelled")
)
sealed interface FlightState {
    fun evolve(event: FlightEvent) : FlightState {
        return when (event) {
            is FlightEvent.FlightScheduledEvent -> ScheduledFlight(event.flightId, event.origin, event.destination)
            is FlightEvent.FlightDelayedEvent -> {
                when (this) {
                    is ScheduledFlight -> DelayedFlight(this.flightId, this.origin, this.destination,
                        listOf(DelayReason.fromEvent(event)))
                    is DelayedFlight -> DelayedFlight(this.flightId, this.origin, this.destination,
                        this.delayReasons + DelayReason.fromEvent(event))
                    else -> this
                }
            }
            is FlightEvent.FlightCancelledEvent -> {
                if (this is ScheduledFlight || this is DelayedFlight) {
                    CancelledFlight(this.flightId, event.reason)
                } else {
                    this
                }
            }
        }
    }

    val flightId: String
    data class EmptyFlight(override val flightId: String) : FlightState
    data class ScheduledFlight(override val flightId: String, val origin: String, val destination: String) : FlightState
    data class DelayedFlight(override val flightId: String, val origin: String, val destination: String, val delayReasons: List<DelayReason>) : FlightState
    data class CancelledFlight(override val flightId: String, val cancelledReason: String) : FlightState

    data class DelayReason (val reason: String, val timestamp: Long) {
        companion object {
            fun fromEvent(event: FlightEvent.FlightDelayedEvent): DelayReason {
                return DelayReason(event.reason, System.currentTimeMillis())
            }
        }
    }

    companion object {
        val Empty = EmptyFlight("")
    }
}

@Singleton
open class Thing {
    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Thing::class.java)
    }
    @NewSpan
    open fun doSomething() {
        log.debug("Doing something in Thing")
    }
}

class FlightAggregate() {

    @Inject
    @Transient
    private lateinit var thing: Thing

    var state: FlightState = FlightState.Empty
    @AggregateIdentifier var aggregateId: String? = null

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



class FlightDeciderAggregateFirstAttempt() {
    var state: FlightState = FlightState.Empty
    @AggregateIdentifier var aggregateId: String? = null


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

class FlightDeciderAggregate : DeciderAggregate<FlightState, FlightCommand, FlightEvent>() {
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

class FlightDeciderAggregate2 : DeciderAggregate2<FlightState, FlightCommand, FlightEvent, FlightDeciderAggregate2>() {
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