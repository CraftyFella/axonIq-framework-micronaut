package com.playground

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.context.annotation.Factory
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.DataSourceConnectionProvider
import org.axonframework.common.transaction.Transaction
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.AggregateConfigurer
import org.axonframework.config.Configuration
import org.axonframework.config.DefaultConfigurer
import org.axonframework.eventhandling.EventBus
import org.axonframework.eventhandling.SimpleEventBus
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore
import org.axonframework.eventhandling.tokenstore.jdbc.PostgresTokenTableFactory
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.PostgresEventTableFactory
import org.axonframework.modelling.saga.repository.SagaStore
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore
import org.axonframework.modelling.saga.repository.jdbc.PostgresSagaSqlSchema
import org.axonframework.queryhandling.QueryBus
import org.axonframework.queryhandling.SimpleQueryBus
import org.axonframework.serialization.json.JacksonSerializer
import org.postgresql.ds.PGSimpleDataSource
import java.util.function.Supplier

@Factory
class AxonFactory {

	@Singleton
	fun dataSource(): PGSimpleDataSource = PGSimpleDataSource().apply {
		setUrl("jdbc:postgresql://localhost:5432/axon_eventstore")
		user = "postgres"
		password = "password"
	}

	@Singleton
	fun connectionProvider(dataSource: PGSimpleDataSource): ConnectionProvider =
		DataSourceConnectionProvider(dataSource)

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


	class LoggingTransactionManager : TransactionManager {

		override fun startTransaction(): Transaction? {
			println("Starting transaction")
			return transaction
		}

		override fun executeInTransaction(task: Runnable?) {
			println("Executing in transaction ${task}")
			super.executeInTransaction(task)
			println("Executed in transaction ${task}")
		}

		override fun <T : Any?> fetchInTransaction(supplier: Supplier<T?>?): T? {
			println("Fetching in transaction ${supplier}")
			val result = super.fetchInTransaction(supplier)
			println("Fetched in transaction ${supplier} ${result}")
			return result
		}

		class LoggingTransaction : Transaction {
			override fun commit() {
				println("Committing transaction")
			}

			override fun rollback() {
				println("Rolling back transaction")
			}
		}

		companion object {
			private val transaction = LoggingTransaction()
			val INSTANCE = LoggingTransactionManager()
		}

	}

	@Singleton
	fun eventStorageEngine(connectionProvider: ConnectionProvider): EventStorageEngine {
		val serializer = jacksonSerializer()

		val engine = JdbcEventStorageEngine
			.builder()
			.snapshotSerializer(serializer)
			.eventSerializer(serializer)
			.connectionProvider(connectionProvider)
			.transactionManager(LoggingTransactionManager.INSTANCE)
			.build()

		engine.createSchema(PostgresEventTableFactory.INSTANCE)
		return engine
	}

//	@Singleton
//	fun eventStore(storageEngine: EventStorageEngine): EventStore {
//		return EmbeddedEventStore.builder()
//			.storageEngine(storageEngine)
//			.build()
//	}

	@Singleton
	fun commandBus(): CommandBus {
		return SimpleCommandBus.builder()
			.transactionManager(LoggingTransactionManager.INSTANCE)
			.build()
	}

	@Singleton
	fun queryBus(): QueryBus {
		return SimpleQueryBus.builder()
			.transactionManager(LoggingTransactionManager.INSTANCE)
			.build()
	}

	@Singleton
	fun eventBus(): EventBus {
		return SimpleEventBus.builder().build()
	}

	@Singleton
	fun sagaStore(connectionProvider: ConnectionProvider): SagaStore<Any> {
		val serializer = jacksonSerializer()

		val sagaStore = JdbcSagaStore.builder()
			.connectionProvider(connectionProvider)
			.sqlSchema(PostgresSagaSqlSchema())
			.serializer(serializer)
			.build()

		// Create the saga store schema
		sagaStore.createSchema()

		return sagaStore
	}

	@Singleton
	fun configuration(
		aysncProjecitonWithStandardProcessingGroup: AysncProjecitonWithStandardProcessingGroup,
		asyncProjectionWithCustomProcessingGroup: AsyncProjectionWithCustomProcessingGroup,
		inLineProjection: InLineProjection,
		storageEngine: EventStorageEngine,
		commandBus: CommandBus,
		queryBus: QueryBus,
		tokenStore: TokenStore,
		sagaStore: SagaStore<Any>
	): Configuration {
		val configurer = DefaultConfigurer.defaultConfiguration(false)
			.configureEmbeddedEventStore { _ -> storageEngine }
			.configureCommandBus { _ -> commandBus }
			.configureQueryBus { _ -> queryBus }
			.configureSerializer { jacksonSerializer() }
			.configureAggregate(
				AggregateConfigurer.defaultConfiguration(FlightAggregate::class.java)
					.configureSnapshotTrigger { c ->
						EventCountSnapshotTriggerDefinition(
							c.snapshotter(), 5
						)
					}
			)
			.eventProcessing { config ->
				config
					.registerTokenStore { _ -> tokenStore }
					.registerSagaStore { _ -> sagaStore }
					.registerSubscribingEventProcessor("other")
					.registerEventHandler { aysncProjecitonWithStandardProcessingGroup }
					.registerEventHandler { asyncProjectionWithCustomProcessingGroup }
					.registerEventHandler { inLineProjection }
					.registerSaga(FlightManagementSaga::class.java)
			}

		return configurer.buildConfiguration()
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

	@Singleton
	class AxonStarter(private val config: Configuration) : ApplicationEventListener<ServerStartupEvent>
	{
		override fun onApplicationEvent(event: ServerStartupEvent?) {
			config.start()
		}

	}

}