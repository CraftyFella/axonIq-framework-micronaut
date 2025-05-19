package com.playground

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.context.annotation.Factory
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.Micronaut.run
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.DataSourceConnectionProvider
import org.axonframework.common.transaction.NoTransactionManager
import org.axonframework.config.Configuration
import org.axonframework.config.DefaultConfigurer
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.PostgresEventTableFactory
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy
import org.axonframework.modelling.command.TargetAggregateIdentifier
import org.axonframework.serialization.json.JacksonSerializer
import org.postgresql.ds.PGSimpleDataSource


fun main(args: Array<String>) {
	run(*args)
}


/*

 Some ideas to make this code better.

 Create a dummy aggregate that we can use to return the new events
 That dummy aggregate has the id and a T for the state.
 The T is created using an evolve function
 The handlers all take the T as a parameter and the command and return n events
 They are passed to a funciotn which uses the apply static to publish those events.
 This way we get the functional event sourcing we crave and also snapshots etc.
 seems you can read the events directly from the event store anyway.

 */

/*
	Try out Live projections (use event store to read events directly)
	subscribing event processor (same thread)
	Streaming event processor (background thread)

	https://docs.axoniq.io/axon-framework-reference/4.10/events/event-processors/

	Try out snapshots
	Try out sagas


	See if I can wire in a transaction manager for POSTGres or Mongo and see if MN has support for jakarta.persistenc whic is what JpaEventStorageEngine uses..

	Ideally though we're going to connect to mongo or postgres.


	check to see if the InMemoryTokenStore is being used.

	TIP:  This scenario is why we recommend storing tokens and projections in the same database.



 */

data class ScheduleFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)

@Controller("/flight")
class FlightController(private val commandGateway: org.axonframework.commandhandling.gateway.CommandGateway) {

	@Get("/schedule/{flightId}")
	fun flight(flightId: String): String {
		val result: Any = commandGateway.sendAndWait(ScheduleFlightCommand(flightId))
		return result.toString()
	}

}

class FlightCommandHandler {

	@CommandHandler
	fun handle(command: ScheduleFlightCommand): String {
		println("Flight scheduled with id: ${command.id}")
		return "Flight scheduled with id: ${command.id}"
	}
}

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

class FlightAggregate {
	@AggregateIdentifier
	private var aggregateId: String? = null

	// other state ...
	@CommandHandler
	@CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
	fun handle(command: ScheduleFlightCommand): String {
		if (aggregateId == null) {
			AggregateLifecycle.apply(FlightScheduledEvent(command.id))
			return "Flight scheduled with id: ${command.id}"
		}else{
			return "Flight scheduled with id: ${command.id} again"
		}

	}

	@EventSourcingHandler
	fun on(event: FlightScheduledEvent) {
		this.aggregateId = event.flightId
		println("Flight scheduled with id: ${event.flightId}")
	}
}


class FlightAggregate2(@AggregateIdentifier private var aggregateId: String? = null) {

    // other state ...
	@CommandHandler
	@CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
	fun handle(command: ScheduleFlightCommand): String {
		if (aggregateId == null) {
			AggregateLifecycle.apply(FlightScheduledEvent(command.id))
			return "Flight scheduled with id: ${command.id}"
		}else{
			return "Flight scheduled with id: ${command.id} again"
		}

	}

	@EventSourcingHandler
	fun on(event: FlightScheduledEvent) {
		this.aggregateId = event.flightId
		println("EventSourcingHandler Flight scheduled with id: ${event.flightId}")
	}
}

@Singleton
class Projection1{

	@EventHandler
	fun on(event: FlightScheduledEvent) {
		println("Projection1 Flight scheduled with id: ${event.flightId}")
	}

}

data class FlightScheduledEvent(val flightId: String)




@Factory
class AxonFactory {


	@Singleton
	fun dataSource(): PGSimpleDataSource = PGSimpleDataSource().apply {
		setUrl("jdbc:postgresql://localhost:5432/axon_eventstore")
		user = "postgres"
		password = "password"
	}

	@Singleton
	fun connectionProvider(dataSource: PGSimpleDataSource): ConnectionProvider = DataSourceConnectionProvider(dataSource)

	@Singleton
	fun eventStoreEngine(connectionProvider: ConnectionProvider): EventStorageEngine {
		val serializer = jacksonSerializer()

		val engine = JdbcEventStorageEngine
			.builder()
			.connectionProvider(connectionProvider)
			.transactionManager(NoTransactionManager.INSTANCE)
			.eventSerializer(serializer)
			.snapshotSerializer(serializer)
			.build()

		engine.createSchema(PostgresEventTableFactory.INSTANCE)
		return engine
	}

//	@Singleton
//	fun eventStore(storageEngine: EventStorageEngine): EventStore {
//		val eventStore: EmbeddedEventStore = EmbeddedEventStore
//			.builder()
//			.storageEngine(storageEngine)
//			.build()
//
//		return eventStore
//	}

	@Singleton
	fun configuration(projection1: Projection1, eventStoreEngine: EventStorageEngine): Configuration {
		val configurer =
		DefaultConfigurer
			.defaultConfiguration()
			.configureEmbeddedEventStore { config -> eventStoreEngine }
			.configureSerializer { jacksonSerializer() }
			.configureAggregate(FlightAggregate2::class.java)
			.eventProcessing {
				it
					.registerTrackingEventProcessor("dave")
					//.registerTrackingEventProcessor("dave") // TODO: Try creating a Persistent stream processor here which support replays
					.registerEventHandler { projection1 }
					//.usingSubscribingEventProcessors()	// Same thread.

			}
			//.registerEventHandler { projection1 }
			//.registerCommandHandler { handler }
			//.registerCommandHandler { handler2 }
			//.registerComponent<>()

		return configurer.start().apply {
			start()
		}
	}

	@Singleton
	fun eventStore(config: Configuration): EventStore {
		return config.eventStore()
	}

	@Singleton
	fun commandGateway(config: Configuration): org.axonframework.commandhandling.gateway.CommandGateway {
		return config.commandGateway()
	}

	private fun jacksonSerializer(): JacksonSerializer {
		return JacksonSerializer
			.builder()
			.objectMapper(
				ObjectMapper().apply {
					registerModule(
                        KotlinModule.Builder()
						.configure(KotlinFeature.NullToEmptyCollection, true)
						.build())
					registerModule(
						JavaTimeModule()
					)
				}

			)
			.build()
	}





}