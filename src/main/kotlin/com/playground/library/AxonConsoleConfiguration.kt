package com.playground.library

import io.axoniq.console.framework.AxoniqConsoleConfigurerModule
import io.axoniq.console.framework.AxoniqConsoleDlqMode
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Factory

@ConfigurationProperties("axoniq.console")
class AxoniqConsoleProperties {
    var environmentId: String? = null
    var accessToken: String? = null
    var applicationName: String = "Default-Application"
    var dlqMode: String = "FULL"
}

@Factory
class AxoniqConsoleConfigurerModuleFactory {

    @Singleton
    @Requires(condition = AxoniqConsoleCondition::class)
    fun axoniqConsoleConfigurerModule(properties: AxoniqConsoleProperties): AxoniqConsoleConfigurerModule {
        return AxoniqConsoleConfigurerModule
            .builder(
                properties.environmentId!!,
                properties.accessToken!!,
                properties.applicationName
            )
            .dlqMode(AxoniqConsoleDlqMode.valueOf(properties.dlqMode))
            .build()
    }

    class AxoniqConsoleCondition : Condition {

        override fun matches(context: ConditionContext<*>?): Boolean {
            val properties = context!!.getBean(AxoniqConsoleProperties::class.java)
            return !properties.environmentId.isNullOrBlank() && !properties.accessToken.isNullOrBlank()
        }
    }
}