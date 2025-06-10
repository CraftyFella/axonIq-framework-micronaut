package com.playground

import io.micronaut.tracing.annotation.NewSpan
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
open class InjectableThing {
    companion object {
        val log: Logger = LoggerFactory.getLogger(InjectableThing::class.java)
    }
    @NewSpan
    open fun doSomething() {
        log.debug("Doing something in Thing")
    }
}