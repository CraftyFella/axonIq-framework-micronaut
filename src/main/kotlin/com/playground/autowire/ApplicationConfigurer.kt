package com.playground.autowire

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Requires
import org.axonframework.config.Configurer

interface ApplicationConfigurer {
    fun configure(configurer: Configurer) : Configurer
}

@Bean
@Requires(missingBeans = [ApplicationConfigurer::class])
class NoOpApplicationConfigurer : ApplicationConfigurer {
    override fun configure(configurer: Configurer): Configurer {
        return configurer
    }
}