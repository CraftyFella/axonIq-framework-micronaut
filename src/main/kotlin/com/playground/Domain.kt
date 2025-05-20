package com.playground

import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy
import org.axonframework.modelling.command.TargetAggregateIdentifier

data class ScheduleFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)
data class DelayFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)
data class CancelFlightCommand(@TargetAggregateIdentifier val id: String /*, other state */)

data class FlightScheduledEvent(val flightId: String)
data class FlightDelayedEvent(val flightId: String)
data class FlightCancelledEvent(val flightId: String)

class FlightAggregate(@AggregateIdentifier var aggregateId: String? = null) {

    var cancelled: Boolean = false

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
        println("EventSourcingHandler Flight scheduled with id: ${event.flightId}")
    }

    @EventSourcingHandler
    fun on(event: FlightDelayedEvent) {
        println("EventSourcingHandler Flight delay with id: ${event.flightId}")
    }

    @EventSourcingHandler
    fun on(event: FlightCancelledEvent) {
        this.cancelled = true
        println("EventSourcingHandler Flight cancel with id: ${event.flightId}")
    }

}