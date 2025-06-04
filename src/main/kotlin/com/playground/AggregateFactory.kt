package com.playground

import io.micronaut.context.ApplicationContext
import jakarta.inject.Singleton
import org.axonframework.config.AggregateConfigurer
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventsourcing.AbstractAggregateFactory
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition

class MicronautAggregateFactory<T>(
    private val applicationContext: ApplicationContext,
    private val aggregateType: Class<T>
) : AbstractAggregateFactory<T>(aggregateType) {

    init {
        // Verify the bean is a prototype at creation time
        val beanDefinition = applicationContext.getBeanDefinition(aggregateType)
        val isPrototypeBean = beanDefinition.isSingleton.not()
        if (!isPrototypeBean) {
            throw IllegalStateException("Aggregate ${aggregateType.simpleName} must be defined with @Prototype scope")
        }
    }

    override fun doCreateAggregate(
        aggregateIdentifier: String?,
        firstEvent: DomainEventMessage<*>?
    ): T? {
        val bean = applicationContext.getBean(aggregateType)
        return bean
    }

    override fun getAggregateType(): Class<T> = aggregateType
}

// Helper class for programmatic registration
@Singleton
class AggregateFactoryHelper(
    private val applicationContext: ApplicationContext
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
        val factory = MicronautAggregateFactory(applicationContext, aggregateType)

        // Return configured AggregateConfigurer
        return AggregateConfigurer
            .defaultConfiguration(aggregateType)
            .configureAggregateFactory { _ -> factory }
            .configureSnapshotTrigger { c ->
                EventCountSnapshotTriggerDefinition(c.snapshotter(), snapshotTriggerThreshold)
            }
    }
}