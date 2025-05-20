package com.playground

import org.axonframework.modelling.saga.SagaEventHandler
import org.axonframework.modelling.saga.SagaLifecycle
import org.axonframework.modelling.saga.StartSaga

class FlightManagementSaga {
    private var cancelled = false
    private var delayed = false

    @StartSaga
    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightScheduledEvent?) {
        println("Saga Flight scheduled: ${event?.flightId}")
    }

    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightDelayedEvent?) {
        delayed = true
        println("Saga FlightDelayedEvent: ${event?.flightId}")
    }

    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightCancelledEvent?) {
        cancelled = true
        println("Saga FlightCancelledEvent: ${event?.flightId}")
        SagaLifecycle.end()
    }
}
