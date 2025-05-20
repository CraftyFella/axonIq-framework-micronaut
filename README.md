## Axon with Micronaut and Postgres

### Functional Event Sourcing Idea

Some ideas to make this code better.

Create a dummy aggregate that we can use to return the new events
That dummy aggregate has the id and a T for the state.
The T is created using an evolve function
The handlers all take the T as a parameter and the command and return n events
They are passed to a funciotn which uses the apply static to publish those events.
This way we get the functional event sourcing we crave and also snapshots etc.
seems you can read the events directly from the event store anyway.

### TODO

* ~~Try out Live projections (use event store to read events directly)~~
* ~~subscribing event processor (same thread)~~
* ~~Streaming event processor (background thread)~~
* ~~Try out snapshots~~
* ~~Try out sagas~~~~~~
* Transactions

### Transactions TODO

	See if I can wire in a transaction manager for POSTGres or Mongo and see if MN has support for jakarta.persistenc whic is what JpaEventStorageEngine uses..

	Ideally though we're going to connect to mongo or postgres.

	TIP:  This scenario is why we recommend storing tokens and projections in the same database.

### Alternative Implementations

Using a command handler instead of the aggregate.

```kotlin
class FlightCommandHandler {

	@CommandHandler
	fun handle(command: ScheduleFlightCommand): String {
		println("Flight scheduled with id: ${command.id}")
		return "Flight scheduled with id: ${command.id}"
	}
}
```

Using the event store directly to pull down a list of events for Live projection

```kotlin
@Singleton
class FlightCommandHandler2(private val eventStore: Provider<EventStore>) {

	@CommandHandler
	fun handle(command: ScheduleFlightCommand): String {
		eventStore.get().readEvents(command.id).forEach { event ->
			println("Event: ${event.payload}")
		}
		return "Flight scheduled2 with id: ${command.id}"
	}
}
```
