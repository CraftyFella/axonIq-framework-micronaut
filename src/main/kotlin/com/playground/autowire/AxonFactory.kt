package com.playground.autowire

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.playground.AsyncProjectionWithCustomProcessingGroup
import com.playground.AysncProjecitonWithStandardProcessingGroup
import com.playground.FlightAggregate
import com.playground.FlightAggregate2
import com.playground.FlightManagementSaga
import com.playground.InLineProjection
import io.micronaut.context.annotation.Factory
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.Configuration
import org.axonframework.config.DefaultConfigurer
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.axonframework.eventhandling.tokenstore.TokenStore
import org.axonframework.eventhandling.tokenstore.jdbc.JdbcTokenStore
import org.axonframework.eventhandling.tokenstore.jdbc.PostgresTokenTableFactory
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore
import org.axonframework.eventsourcing.eventstore.EventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine
import org.axonframework.eventsourcing.eventstore.jdbc.PostgresEventTableFactory
import org.axonframework.messaging.unitofwork.RollbackConfigurationType
import org.axonframework.modelling.saga.repository.SagaStore
import org.axonframework.modelling.saga.repository.jdbc.JdbcSagaStore
import org.axonframework.modelling.saga.repository.jdbc.PostgresSagaSqlSchema
import org.axonframework.queryhandling.QueryBus
import org.axonframework.queryhandling.SimpleQueryBus
import org.axonframework.serialization.json.JacksonSerializer
import org.axonframework.tracing.SpanFactory
import org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory

@Factory
class AxonFactory() {

    @Singleton
    fun tokenStore(connectionProvider: ConnectionProvider): TokenStore {
        val tokenStore =
            JdbcTokenStore.builder().connectionProvider(connectionProvider).serializer(jacksonSerializer()).build()

        // Create the token store schema
        tokenStore.createSchema(PostgresTokenTableFactory.INSTANCE)

        return tokenStore
    }

    @Singleton
    fun eventStorageEngine(
        connectionProvider: ConnectionProvider, transactionManager: TransactionManager
    ): EventStorageEngine {
        val serializer = jacksonSerializer()

        val engine = JdbcEventStorageEngine.builder()
            .snapshotSerializer(serializer)
            .eventSerializer(serializer)
            .connectionProvider(connectionProvider)
            .transactionManager(transactionManager)
            .build()

        engine.createSchema(PostgresEventTableFactory.INSTANCE)
        return engine
    }

    @Singleton
    fun commandBus(spanFactory: SpanFactory, transactionManager: TransactionManager): CommandBus =
        SimpleCommandBus.builder()
            .rollbackConfiguration(RollbackConfigurationType.ANY_THROWABLE)
            .spanFactory(spanFactory)
            .transactionManager(transactionManager).build()

    @Singleton
    fun queryBus(spanFactory: SpanFactory, transactionManager: TransactionManager): QueryBus {
        return SimpleQueryBus.builder()
            .spanFactory(spanFactory)
            .transactionManager(transactionManager).build()
    }

    @Singleton
    fun sagaStore(connectionProvider: ConnectionProvider): SagaStore<Any> {
        val serializer = jacksonSerializer()

        val sagaStore =
            JdbcSagaStore.builder()
                .connectionProvider(connectionProvider)
                .sqlSchema(PostgresSagaSqlSchema())
                .serializer(serializer).build()

        // Create the saga store schema
        sagaStore.createSchema()

        return sagaStore
    }


    @Singleton
    fun tracer(otel: OpenTelemetry): Tracer {
        return otel.getTracer("AxonFramework-OpenTelemetry")
    }

    @Singleton
    fun spanFactory(tracer: Tracer): SpanFactory {
        return OpenTelemetrySpanFactory.builder().tracer(tracer).build()
    }

    @Singleton
    fun eventStore(spanFactory: SpanFactory, storageEngine: EventStorageEngine): EmbeddedEventStore =
        EmbeddedEventStore.builder()
            .spanFactory(spanFactory)
            .storageEngine(storageEngine)
            .build()


    @Singleton
    fun configuration(
        asyncProjectionWithStandardProcessingGroup: AysncProjecitonWithStandardProcessingGroup,
        asyncProjectionWithCustomProcessingGroup: AsyncProjectionWithCustomProcessingGroup,
        inLineProjection: InLineProjection,
        commandBus: CommandBus,
        queryBus: QueryBus,
        tokenStore: TokenStore,
        sagaStore: SagaStore<Any>,
        spanFactory: SpanFactory,
        eventStore: EmbeddedEventStore,
        aggregateFactoryHelper: MicronautAggregateConfigurer,
        micronautResourceInjector: MicronautResourceInjector
    ): Configuration {
        val configurer = DefaultConfigurer.defaultConfiguration(false)
            .configureSpanFactory { spanFactory }
            .configureEventStore { c ->
                c.onShutdown { eventStore.shutDown() }
                eventStore
            }
            .configureCommandBus { _ -> commandBus }
            .configureQueryBus { _ -> queryBus }
            .configureSerializer { jacksonSerializer() }
            .configureResourceInjector { micronautResourceInjector }
            .configureAggregate(aggregateFactoryHelper.configurationFor(FlightAggregate2::class.java))
            .eventProcessing { config ->
                config
                    .registerTokenStore { _ -> tokenStore }
                    .registerSagaStore { _ -> sagaStore }
                    .registerSubscribingEventProcessor(InLineProjection.NAME)
                    .registerEventHandler { asyncProjectionWithStandardProcessingGroup }
                    .registerEventHandler { asyncProjectionWithCustomProcessingGroup }
                    .registerEventHandler { inLineProjection }
                    .registerListenerInvocationErrorHandler(InLineProjection.NAME) { PropagatingErrorHandler.INSTANCE }
                    .registerSaga(FlightManagementSaga::class.java)
            }

        return configurer.buildConfiguration()
    }

    @Singleton
    fun commandGateway(config: Configuration): CommandGateway {
        return config.commandGateway()
    }

    private fun jacksonSerializer(): JacksonSerializer {
        return JacksonSerializer.builder().objectMapper(
            ObjectMapper().apply {
                registerModule(
                    KotlinModule.Builder().configure(KotlinFeature.NullToEmptyCollection, true).build()
                )
                registerModule(JavaTimeModule())
            }).build()
    }

    @Singleton
    class AxonStarter(private val config: Configuration) : ApplicationEventListener<ServerStartupEvent> {
        override fun onApplicationEvent(event: ServerStartupEvent?) {
            config.start()
        }
    }

}