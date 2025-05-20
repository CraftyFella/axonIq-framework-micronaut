package com.playground

import jakarta.inject.Singleton
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler

@Singleton
class Projection1 {

    @EventHandler
    fun on(event: FlightScheduledEvent) {
        println("Projection1 Flight scheduled with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightDelayedEvent) {
        println("Projection1 Flight delayed with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightCancelledEvent) {
        println("Projection1 Flight cancelled with id: ${event.flightId}")
    }
}

@Singleton
@ProcessingGroup("Projection2")
class Projection2 {

    @EventHandler
    fun on(event: FlightScheduledEvent) {
        println("Projection2 Flight scheduled with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightDelayedEvent) {
        println("Projection2 Flight delayed with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightCancelledEvent) {
        println("Projection2 Flight cancelled with id: ${event.flightId}")
    }
}


@Singleton
@ProcessingGroup("other")
class Projection3 {

    @EventHandler
    fun on(event: FlightScheduledEvent) {
        println("Projection3 Flight scheduled with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightDelayedEvent) {
        println("Projection3 Flight delayed with id: ${event.flightId}")
    }

    @EventHandler
    fun on(event: FlightCancelledEvent) {
        println("Projection3 Flight cancelled with id: ${event.flightId}")
    }
}
