package com.playground

import jakarta.inject.Inject
import org.axonframework.modelling.saga.SagaEventHandler
import org.axonframework.modelling.saga.SagaLifecycle
import org.axonframework.modelling.saga.StartSaga

class FlightManagementSaga {
    var cancelled = false
    var delayed = false

    @Inject
    private lateinit var thing: Thing

    @StartSaga
    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightScheduledEvent?) {
        println("Saga Flight scheduled: ${event?.flightId}")
    }

    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightDelayedEvent?) {
        delayed = true
        thing.doSomething() // Example usage of injected dependency
        println("Saga FlightDelayedEvent: ${event?.flightId}")
    }

    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightCancelledEvent?) {
        cancelled = true
        println("Saga FlightCancelledEvent: ${event?.flightId}")
        SagaLifecycle.end()
    }
}
