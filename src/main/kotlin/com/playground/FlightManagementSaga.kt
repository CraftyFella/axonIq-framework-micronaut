package com.playground

import jakarta.inject.Inject
import org.axonframework.modelling.saga.SagaEventHandler
import org.axonframework.modelling.saga.SagaLifecycle
import org.axonframework.modelling.saga.StartSaga

class FlightManagementSaga {
    var cancelled = false
    var delayed = false

    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(FlightManagementSaga::class.java)
    }

    @Inject
    private lateinit var thing: InjectableThing

    @StartSaga
    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightEvent.FlightScheduledEvent?) {
        log.debug("Saga Flight scheduled: ${event?.flightId}")
    }

    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightEvent.FlightDelayedEvent?) {
        delayed = true
        thing.doSomething() // Example usage of injected dependency
        log.debug("Saga FlightDelayedEvent: ${event?.flightId}")
    }

    @SagaEventHandler(associationProperty = "flightId")
    fun handle(event: FlightEvent.FlightCancelledEvent?) {
        cancelled = true
        log.debug("Saga FlightCancelledEvent: ${event?.flightId}")
        SagaLifecycle.end()
    }
}
