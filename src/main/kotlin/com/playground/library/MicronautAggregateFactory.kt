package com.playground.library

import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition
import jakarta.inject.Singleton
import org.axonframework.config.AggregateConfigurer
import org.axonframework.config.Configurer
import org.axonframework.eventsourcing.EventCountSnapshotTriggerDefinition
import org.axonframework.eventsourcing.GenericAggregateFactory
import org.axonframework.modelling.command.CreationPolicyAggregateFactory
import org.axonframework.modelling.command.NoArgumentConstructorCreationPolicyAggregateFactory

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
        val aggregateFactory = MicronautAggregateFactory(beanContext, aggregateType)
        val creationPolicyAggregateFactory = MicronautCreationPolicyAggregateFactory(beanContext, aggregateType)

        // Return configured AggregateConfigurer
        return AggregateConfigurer
            .defaultConfiguration(aggregateType)
            .configureAggregateFactory { _ -> aggregateFactory }
            .configureCreationPolicyAggregateFactory { creationPolicyAggregateFactory }
            .configureSnapshotTrigger { c ->
                EventCountSnapshotTriggerDefinition(c.snapshotter(), snapshotTriggerThreshold)
            }
    }
}

class MicronautCreationPolicyAggregateFactory<A>(
    private val beanContext: BeanContext,
    aggregateClass: Class<out A>
) : CreationPolicyAggregateFactory<A> {

    private val delegateFactory = NoArgumentConstructorCreationPolicyAggregateFactory(aggregateClass)

    override fun create(identifier: Any?): A & Any {
        val aggregate = delegateFactory.create(identifier)
        return beanContext.inject(aggregate)
    }
}

// Extension function for configureAggregate
fun <T> Configurer.registerAggregateUsingConfigurer(
    configurer: MicronautAggregateConfigurer,
    aggregateType: Class<T>,
    maxSequences: Int = 100,
    maxSequenceSize: Int = 1000
): Configurer = this.configureAggregate(configurer.configurationFor(aggregateType))