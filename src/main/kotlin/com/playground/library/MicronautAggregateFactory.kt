package com.playground.library

import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton
import org.axonframework.config.AggregateConfigurer
import org.axonframework.config.Configurer
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition
import org.axonframework.eventsourcing.GenericAggregateFactory

open class MicronautAggregateFactory<T>(
    private val beanContext: BeanContext,
    aggregateType: Class<T>
) : GenericAggregateFactory<T>(aggregateType) {

    init {
        // Check if the aggregate type has a scope (singleton, prototype, etc.)
        val beanDefinitionOption = beanContext.findBeanDefinition(aggregateType)
        beanDefinitionOption.ifPresent { beanDefinition ->
            if (beanDefinition.isSingleton() || hasScopeAnnotation(beanDefinition)) {
                throw IllegalStateException(
                    "Aggregate ${aggregateType.simpleName} should not be a scoped bean " +
                            "(singleton, prototype, etc). Remove scope annotations but keep @Inject for constructor injection."
                )
            }
        }
    }

    private fun hasScopeAnnotation(beanDefinition: BeanDefinition<*>): Boolean {
        // Check for common scope annotations
        return beanDefinition.hasAnnotation("io.micronaut.context.annotation.Prototype") ||
                beanDefinition.hasAnnotation("io.micronaut.runtime.context.scope.ScopedProxy") ||
                beanDefinition.hasAnnotation("io.micronaut.runtime.http.scope.RequestScope") ||
                beanDefinition.hasStereotype("jakarta.inject.Singleton")
    }

    override fun postProcessInstance(aggregate: T?): T? {
        return beanContext.inject(aggregate)
    }
}

// Helper class for programmatic registration
@Singleton
class MicronautAggregateConfigurer(
    private val beanContext: BeanContext
) {

    /**
     * Creates and registers an AggregateFactory for the given aggregate type,
     * then returns a configured AggregateConfigurer
     */
    fun <T> configurationFor(
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

// Extension function for configureAggregate
fun <T> Configurer.configureAggregateUsingConfigurer(
    configurer: MicronautAggregateConfigurer,
    aggregateType: Class<T>,
    maxSequences: Int = 100,
    maxSequenceSize: Int = 1000
): Configurer = this.configureAggregate(configurer.configurationFor(aggregateType))