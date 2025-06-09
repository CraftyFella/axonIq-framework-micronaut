package com.playground

import com.playground.AysncProjecitonWithStandardProcessingGroup.Companion.log
import jakarta.inject.Singleton
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler

@Singleton
class AysncProjecitonWithStandardProcessingGroup {

    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(AysncProjecitonWithStandardProcessingGroup::class.java)
    }

    @EventHandler
    fun on(event: FlightEvent.FlightScheduledEvent) {
        log.debug("Projection1 Flight scheduled with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightEvent.FlightDelayedEvent) {
        log.debug("Projection1 Flight delayed with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        log.debug("Projection1 Flight cancelled with id: ${event.flightId}")
    }
}

@Singleton
@ProcessingGroup("Projection2")
class AsyncProjectionWithCustomProcessingGroup {

    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(AysncProjecitonWithStandardProcessingGroup::class.java)
    }

    @EventHandler
    fun on(event: FlightEvent.FlightScheduledEvent) {
        log.debug("Projection2 Flight scheduled with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightEvent.FlightDelayedEvent) {
        log.debug("Projection2 Flight delayed with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        log.debug("Projection2 Flight cancelled with id: ${event.flightId}")
    }
}

