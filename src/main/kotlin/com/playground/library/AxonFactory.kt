package com.playground.library

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.axoniq.console.framework.AxoniqConsoleConfigurerModule
import io.micronaut.context.annotation.Factory
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import jakarta.annotation.Nullable
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandBus
import org.axonframework.commandhandling.SimpleCommandBus
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.Configuration
import org.axonframework.config.Configurer
import org.axonframework.config.DefaultConfigurer
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
import org.axonframework.queryhandling.QueryGateway
import org.axonframework.queryhandling.SimpleQueryBus
import org.axonframework.serialization.Serializer
import org.axonframework.serialization.json.JacksonSerializer
import org.axonframework.tracing.SpanFactory
import org.axonframework.tracing.opentelemetry.OpenTelemetrySpanFactory

@Factory
class AxonFactory() {

    @Singleton
    fun tokenStore(connectionProvider: ConnectionProvider, serializer: Serializer): TokenStore {
        val tokenStore =
            JdbcTokenStore.builder().connectionProvider(connectionProvider).serializer(serializer).build()

        // Create the token store schema
        tokenStore.createSchema(PostgresTokenTableFactory.INSTANCE)

        return tokenStore
    }

    @Singleton
    fun eventStorageEngine(
        connectionProvider: ConnectionProvider, transactionManager: TransactionManager, serializer: Serializer
    ): EventStorageEngine {
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
    fun sagaStore(connectionProvider: ConnectionProvider, serializer: Serializer): SagaStore<Any> {

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
        commandBus: CommandBus,
        queryBus: QueryBus,
        tokenStore: TokenStore,
        sagaStore: SagaStore<Any>,
        spanFactory: SpanFactory,
        eventStore: EmbeddedEventStore,
        micronautResourceInjector: MicronautResourceInjector,
        @Nullable axoniqConsoleConfigurerModule: AxoniqConsoleConfigurerModule?,
        applicationConfigurer: ApplicationConfigurer,
        serializer: Serializer
    ): Configuration {
        val configurer: Configurer = DefaultConfigurer.defaultConfiguration(false)
            .configureSpanFactory { spanFactory }
            .configureEventStore { c ->
                c.onShutdown { eventStore.shutDown() }
                eventStore
            }
            .configureCommandBus { _ -> commandBus }
            .configureQueryBus { _ -> queryBus }
            .configureSerializer { serializer }
            .configureResourceInjector { micronautResourceInjector }
            .eventProcessing { config ->
                config
                    .registerTokenStore { _ -> tokenStore }
                    .registerSagaStore { _ -> sagaStore }
            }

        axoniqConsoleConfigurerModule?.configureModule(configurer)
        applicationConfigurer.configure(configurer)

        return configurer.buildConfiguration()
    }

    @Singleton
    fun commandGateway(config: Configuration): CommandGateway {
        return config.commandGateway()
    }

    @Singleton
    fun queryGateway(config: Configuration): QueryGateway {
        return config.queryGateway()
    }

    @Singleton
    fun jacksonSerializer(): Serializer {
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