package com.playground

import io.micronaut.context.annotation.Prototype
import io.micronaut.tracing.annotation.NewSpan
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.AggregateRoot
import org.axonframework.modelling.command.CreationPolicy
import org.axonframework.modelling.command.TargetAggregateIdentifier

data class ScheduleFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)
data class DelayFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)
data class CancelFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)

data class FlightScheduledEvent(val flightId: String)
data class FlightDelayedEvent(val flightId: String)
data class FlightCancelledEvent(val flightId: String)

@Singleton
open class Thing {
    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Thing::class.java)
    }
    @NewSpan
    open fun doSomething() {
        log.debug("Doing something in Thing")
    }
}

class FlightAggregate() {

    @Inject
    @Transient
    private lateinit var thing: Thing

    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(FlightAggregate::class.java)
    }

    var cancelled: Boolean = false
    @AggregateIdentifier var aggregateId: String? = null

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    fun handle(command: ScheduleFlightCommand): String {
        if (aggregateId == null) {
            AggregateLifecycle.apply(FlightScheduledEvent(command.id))
            return "Flight scheduled with id: ${command.id}"
        } else {
            return "Flight scheduled with id: ${command.id} again"
        }
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.NEVER)
    fun handle(command: CancelFlightCommand): String {
        if (cancelled) {
            throw IllegalStateException("Flight already cancelled")
        }
        AggregateLifecycle.apply(FlightCancelledEvent(command.id))
        return "Flight cancelled with id: ${command.id}"
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.NEVER)
    fun handle(command: DelayFlightCommand): String {
        if (cancelled) {
            throw IllegalStateException("Flight already cancelled")
        }
        AggregateLifecycle.apply(FlightDelayedEvent(command.id))
        return "Flight delayed with id: ${command.id}"
    }

    @EventSourcingHandler
    fun on(event: FlightScheduledEvent) {
        this.aggregateId = event.flightId
        log.debug("EventSourcingHandler Flight scheduled with id: ${event.flightId}")
    }

    @EventSourcingHandler
    open fun on(event: FlightDelayedEvent) {
        thing.doSomething()
        log.debug("EventSourcingHandler Flight delay with id: ${event.flightId}")
    }

    @EventSourcingHandler
    fun on(event: FlightCancelledEvent) {
        this.cancelled = true
        log.debug("EventSourcingHandler Flight cancel with id: ${event.flightId}")
    }

}