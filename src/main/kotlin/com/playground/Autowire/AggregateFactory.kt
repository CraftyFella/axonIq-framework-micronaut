package com.playground.Autowire

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton
import org.axonframework.common.Assert
import org.axonframework.config.AggregateConfigurer
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventsourcing.AggregateFactory
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition
import org.axonframework.eventsourcing.IncompatibleAggregateException
import org.axonframework.modelling.command.inspection.AggregateModel
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory
import java.lang.reflect.Modifier

/**
 * Micronaut-aware AggregateFactory that always uses the bean context to create aggregate instances,
 * ensuring proper dependency injection and AOT support. Handles both regular aggregate creation
 * and snapshot restoration.
 *
 * @param T The type of aggregate this factory creates
 */
open class MicronautAggregateFactory<T>(
    private val beanContext: BeanContext,
    private val aggregateType: Class<T>,
    private val objectMapper: ObjectMapper = ObjectMapper()
) : AggregateFactory<T> {

    private val aggregateModel: AggregateModel<T>

    init {
        // Verify the bean is a prototype at creation time
        val beanDefinition: BeanDefinition<T> = beanContext.getBeanDefinition(aggregateType)
        val isPrototypeBean = !beanDefinition.isSingleton
        if (!isPrototypeBean) {
            throw IllegalStateException("Aggregate ${aggregateType.simpleName} must be defined with @Prototype scope")
        }

        // Verify the aggregate type is not abstract (similar to GenericAggregateFactory)
        Assert.isFalse(
            Modifier.isAbstract(aggregateType.modifiers)
        ) { "Given aggregateType may not be abstract" }

        // Initialize the aggregate model
        this.aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateType)
    }

    override fun createAggregateRoot(aggregateIdentifier: String, firstEvent: DomainEventMessage<*>): T {
        // Always create a new instance using the bean context for proper DI
        val aggregate = createBeanInstance()

        // Check if the first event is a snapshot
        return if (isSnapshotEvent(firstEvent)) {
            // Restore from snapshot - merge snapshot data into the bean instance
            restoreFromSnapshot(aggregate, firstEvent)
        } else {
            // Return the fresh bean instance for regular event sourcing
            aggregate
        }
    }

    override fun getAggregateType(): Class<T> = aggregateType

    /**
     * Creates a new aggregate instance using Micronaut's bean context.
     * This ensures proper dependency injection and AOT support.
     */
    private fun createBeanInstance(): T {
        try {
            return beanContext.getBean(aggregateType)
        } catch (e: Exception) {
            throw IncompatibleAggregateException(
                "Failed to create aggregate instance of type [${aggregateType.simpleName}] " +
                        "using Micronaut bean context. Ensure the aggregate is properly configured as a @Prototype bean.",
                e
            )
        }
    }

    /**
     * Checks if the given event is a snapshot event by verifying if its payload type
     * matches any of the aggregate types in the model.
     */
    private fun isSnapshotEvent(event: DomainEventMessage<*>): Boolean {
        return aggregateModel.types().anyMatch { type ->
            event.payloadType == type
        }
    }

    /**
     * Restores an aggregate from a snapshot by merging the snapshot data into
     * a fresh bean instance created from the bean context.
     */
    private fun restoreFromSnapshot(aggregate: T, snapshotEvent: DomainEventMessage<*>): T {
        try {
            val snapshotPayload = snapshotEvent.payload

            // Use Jackson to merge the snapshot data into the bean instance
            // This preserves the bean's injected dependencies while restoring its state
            val aggregateJson = objectMapper.writeValueAsString(snapshotPayload)
            val restoredAggregate = objectMapper.readerForUpdating(aggregate)
                .readValue<T>(aggregateJson)

            return restoredAggregate
        } catch (e: Exception) {
            throw IncompatibleAggregateException(
                "Failed to restore aggregate [${aggregateType.simpleName}] from snapshot. " +
                        "Ensure the aggregate is properly serializable and the snapshot format is compatible.",
                e
            )
        }
    }

}

// Helper class for programmatic registration
@Singleton
class AggregateFactoryHelper(
    private val beanContext: BeanContext
) {

    /**
     * Creates and registers an AggregateFactory for the given aggregate type,
     * then returns a configured AggregateConfigurer
     */
    fun <T> createAggregateFactoryFor(
        aggregateType: Class<T>,
        snapshotTriggerThreshold: Int = 5
    ): AggregateConfigurer<T> {

        // Create the factory instance
        val factory = MicronautAggregateFactory(beanContext, aggregateType)

        // Return configured AggregateConfigurer
        return AggregateConfigurer
            .defaultConfiguration(aggregateType)
            .configureAggregateFactory { _ -> factory }
            .configureSnapshotTrigger { c ->
                EventCountSnapshotTriggerDefinition(c.snapshotter(), snapshotTriggerThreshold)
            }
    }
}