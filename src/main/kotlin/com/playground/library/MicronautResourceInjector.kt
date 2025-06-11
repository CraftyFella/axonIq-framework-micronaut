package com.playground.library

import io.micronaut.context.BeanContext
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.axonframework.config.ConfigurationResourceInjector
import org.axonframework.modelling.saga.ResourceInjector
import org.axonframework.config.Configuration

@Singleton
class MicronautResourceInjector(
    private val beanContext: BeanContext,
    private val configuration: Provider<Configuration>
) : ResourceInjector {

    private val fallbackInjector: ConfigurationResourceInjector by lazy {
        ConfigurationResourceInjector(configuration.get())
    }

    override fun injectResources(saga: Any) {
        // First try to inject using Micronaut's BeanContext
        try {
            beanContext.inject(saga)
        } catch (e: Exception) {
            // If Micronaut injection fails, fall back to Axon's configuration-based injection
            fallbackInjector.injectResources(saga)
        }
    }
}