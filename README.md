# Axon with Micronaut and Postgres

## Features

### Done

* ~~Try out Live projections (use event store to read events directly)~~
* ~~subscribing event processor (same thread)~~
* ~~Streaming event processor (background thread)~~
* ~~Try out snapshots~~
* ~~Try out sagas~~
* ~~Transactions~~
* ~~Micronaut Aggregate Factory~~
* ~~Micronaut Saga Factory~~
* ~~Fix connection leak~~
* ~~Decider concepts~~
* ~~Have IO in the streaming projections~~
  * ~~Scheduled Flights by Origin~~
  * ~~Scheduled Flights by Destination~~
  * ~~Flights delay count and status~~
* ~~Query handlers for the projections.~~  
### To Do

* Multi node projections (leadership)
* Scale out projections (Sharding projections)
* Replay projections (streaming)
* Load Test
* Transaction Manager playing nicely with Micronaut data (Nice to have, can work around this TBH)

## Deciders

I can think of 3 options for the decider pattern with Axon and Micronaut:

### Option 1: Just use an evolve method for events

```kotlin
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
```

Downs side are you don't get compile time help for exhaustive pattern matching on the commands, however you do for the events. pros are it's quite simple to setup and leans on the library to do the work. Also don't need to wrap the commands in a CommandMessage.

### Option 2: Sum Type for Commands and Events

```kotlin
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
```

### Option 3: Explicit Decider and DeciderAggregate to split the concerns

When dispatching a command, instead of using the default concreate command when sending instead wrap it in a commandmessage as follows:

```kotlin
private fun <T> sendCommandAsSumType(command: T, clazz: Class<T>): String {
		val commandMessage = GenericCommandMessage(GenericMessage<T?>(command, MetaData.emptyInstance()), clazz.name)
		val result: Any = commandGateway.sendAndWait(commandMessage)
		return result.toString()
	}
```

With this you can write your decider as follows:

```kotlin
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
```

and then to wire it up with Axon you can do the following:

```kotlin
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
```
