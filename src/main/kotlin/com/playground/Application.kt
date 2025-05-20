package com.playground

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.context.annotation.Factory
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.Micronaut.run
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.DataSourceConnectionProvider
import org.axonframework.common.transaction.NoTransactionManager
import org.axonframework.config.AggregateConfigurer
import org.axonframework.config.Configuration
import org.axonframework.config.DefaultConfigurer
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventhandling.SimpleEventBus
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore
import org.axonframework.eventhandling.tokenstore.jdbc.PostgresTokenTableFactory
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.PostgresEventTableFactory
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy
import org.axonframework.modelling.command.TargetAggregateIdentifier
import org.axonframework.queryhandling.QueryBus
import org.axonframework.queryhandling.SimpleQueryBus
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
data class DelayFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)
data class CancelFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)

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

//class FlightCommandHandler {
//
//	@CommandHandler
//	fun handle(command: ScheduleFlightCommand): String {
//		println("Flight scheduled with id: ${command.id}")
//		return "Flight scheduled with id: ${command.id}"
//	}
//}

//@Singleton
//class FlightCommandHandler2(private val eventStore: Provider<EventStore>) {
//
//	@CommandHandler
//	fun handle(command: ScheduleFlightCommand): String {
//		eventStore.get().readEvents(command.id).forEach { event ->
//			println("Event: ${event.payload}")
//		}
//		return "Flight scheduled2 with id: ${command.id}"
//	}
//}

class FlightAggregate(@AggregateIdentifier var aggregateId: String? = null) {

	var cancelled: Boolean = false

	@CommandHandler
	@CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
	fun handle(command: ScheduleFlightCommand): String {
		if (aggregateId == null) {
			AggregateLifecycle.apply(FlightScheduledEvent(command.id))
			return "Flight scheduled with id: ${command.id}"
		} else {
			return "Flight scheduled with id: ${command.id} again"
		}
	}

	@CommandHandler
	@CreationPolicy(AggregateCreationPolicy.NEVER)
	fun handle(command: CancelFlightCommand): String {
		if (cancelled) {
			throw IllegalStateException("Flight already cancelled")
		}
		AggregateLifecycle.apply(FlightCancelledEvent(command.id))
		return "Flight cancelled with id: ${command.id}"
	}

	@CommandHandler
	@CreationPolicy(AggregateCreationPolicy.NEVER)
	fun handle(command: DelayFlightCommand): String {
		if (cancelled) {
			throw IllegalStateException("Flight already cancelled")
		}
		AggregateLifecycle.apply(FlightDelayedEvent(command.id))
		return "Flight delayed with id: ${command.id}"
	}

	@EventSourcingHandler
	fun on(event: FlightScheduledEvent) {
		this.aggregateId = event.flightId
		println("EventSourcingHandler Flight scheduled with id: ${event.flightId}")
	}

	@EventSourcingHandler
	fun on(event: FlightDelayedEvent) {
		println("EventSourcingHandler Flight delay with id: ${event.flightId}")
	}

	@EventSourcingHandler
	fun on(event: FlightCancelledEvent) {
		this.cancelled = true
		println("EventSourcingHandler Flight cancel with id: ${event.flightId}")
	}

}

@Singleton
class Projection1 {

	@EventHandler
	fun on(event: FlightScheduledEvent) {
		println("Projection1 Flight scheduled with id: ${event.flightId}")
	}

	@EventHandler
	fun on(event: FlightDelayedEvent) {
		println("Projection1 Flight delayed with id: ${event.flightId}")
	}

	@EventHandler
	fun on(event: FlightCancelledEvent) {
		println("Projection1 Flight cancelled with id: ${event.flightId}")
	}
}

@Singleton
@ProcessingGroup("Projection2")
class Projection2 {

	@EventHandler
	fun on(event: FlightScheduledEvent) {
		println("Projection2 Flight scheduled with id: ${event.flightId}")
	}

	@EventHandler
	fun on(event: FlightDelayedEvent) {
		println("Projection2 Flight delayed with id: ${event.flightId}")
	}

	@EventHandler
	fun on(event: FlightCancelledEvent) {
		println("Projection2 Flight cancelled with id: ${event.flightId}")
	}
}


@Singleton
@ProcessingGroup("other")
class Projection3 {

	@EventHandler
	fun on(event: FlightScheduledEvent) {
		println("Projection3 Flight scheduled with id: ${event.flightId}")
	}

	@EventHandler
	fun on(event: FlightDelayedEvent) {
		println("Projection3 Flight delayed with id: ${event.flightId}")
	}

	@EventHandler
	fun on(event: FlightCancelledEvent) {
		println("Projection3 Flight cancelled with id: ${event.flightId}")
	}
}

data class FlightScheduledEvent(val flightId: String)
data class FlightDelayedEvent(val flightId: String)
data class FlightCancelledEvent(val flightId: String)

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
	fun tokenStore(connectionProvider: ConnectionProvider): TokenStore {
		val tokenStore = JdbcTokenStore.builder()
			.connectionProvider(connectionProvider)
			.serializer(jacksonSerializer())
			.build()

		// Create the token store schema
		tokenStore.createSchema(PostgresTokenTableFactory.INSTANCE)

		return tokenStore
	}

	@Singleton
	fun eventStorageEngine(connectionProvider: ConnectionProvider): EventStorageEngine {
		val serializer = jacksonSerializer()

		val engine = JdbcEventStorageEngine.builder()
			.connectionProvider(connectionProvider)
			.transactionManager(NoTransactionManager.INSTANCE)
			.eventSerializer(serializer)
			.snapshotSerializer(serializer)
			.build()

		engine.createSchema(PostgresEventTableFactory.INSTANCE)
		return engine
	}

	@Singleton
	fun eventStore(storageEngine: EventStorageEngine): EventStore {
		return EmbeddedEventStore.builder()
			.storageEngine(storageEngine)
			.build()
	}

	@Singleton
	fun commandBus(): CommandBus {
		return SimpleCommandBus.builder()
			.transactionManager(NoTransactionManager.INSTANCE)
			.build()
	}

	@Singleton
	fun queryBus(): QueryBus {
		return SimpleQueryBus.builder()
			.transactionManager(NoTransactionManager.INSTANCE)
			.build()
	}

	@Singleton
	fun eventBus(): EventBus {
		return SimpleEventBus.builder().build()
	}

	@Singleton
	fun configuration(
		projection1: Projection1,
		projection2: Projection2,
		projection3: Projection3,
		eventStore: EventStore,
		commandBus: CommandBus,
		queryBus: QueryBus,
		tokenStore: TokenStore
	): Configuration {
		val configurer = DefaultConfigurer.defaultConfiguration()
			.configureEventStore { _ -> eventStore }
			.configureCommandBus { _ -> commandBus }
			.configureQueryBus { _ -> queryBus }
			.configureSerializer { jacksonSerializer() }
			.configureAggregate(				AggregateConfigurer.defaultConfiguration(FlightAggregate::class.java)
				.configureSnapshotTrigger { c ->
					EventCountSnapshotTriggerDefinition(
						c.snapshotter(), 5
					)
				}
			)

			.eventProcessing { config ->
				config
					.registerTokenStore { _ -> tokenStore }
					.registerTrackingEventProcessor("dave3")
					.registerEventHandler { projection1 }
					.registerTrackingEventProcessor("dave2")
					.registerEventHandler { projection2 }
					.registerSubscribingEventProcessor("other")
					.registerEventHandler { projection3 }

			}

		return configurer.buildConfiguration().also {
			it.start()
		}
	}

	@Singleton
	fun commandGateway(config: Configuration): CommandGateway {
		return config.commandGateway()
	}

	private fun jacksonSerializer(): JacksonSerializer {
		return JacksonSerializer.builder()
			.objectMapper(
				ObjectMapper().apply {
					registerModule(
						KotlinModule.Builder()
							.configure(KotlinFeature.NullToEmptyCollection, true)
							.build()
					)
					registerModule(JavaTimeModule())
				}
			)
			.build()
	}
}