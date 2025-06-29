package com.playground

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.tracing.annotation.NewSpan
import jakarta.inject.Singleton
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventsourcing.EventSourcingHandler
import org.axonframework.modelling.command.AggregateCreationPolicy
import org.axonframework.modelling.command.AggregateIdentifier
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.CreationPolicy
import org.axonframework.modelling.command.TargetAggregateIdentifier

sealed interface FlightCommand {
    val flightId: String
    data class ScheduleFlightCommand(@TargetAggregateIdentifier override val flightId: String, val flightNumber: String, val origin: String, val destination: String) : FlightCommand
    data class DelayFlightCommand(@TargetAggregateIdentifier override val flightId: String, val reason: String): FlightCommand
    data class CancelFlightCommand(@TargetAggregateIdentifier override val flightId: String, val reason: String): FlightCommand
}

sealed interface FlightEvent {
    val flightId: String
    data class FlightScheduledEvent(override val flightId: String,  val flightNumber: String, val origin: String, val destination: String) : FlightEvent
    data class FlightDelayedEvent(override val flightId: String, val reason: String) : FlightEvent
    data class FlightCancelledEvent(override val flightId: String, val reason: String) : FlightEvent
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FlightState.EmptyFlight::class, name = "empty"),
    JsonSubTypes.Type(value = FlightState.ScheduledFlight::class, name = "scheduled"),
    JsonSubTypes.Type(value = FlightState.DelayedFlight::class, name = "delayed"),
    JsonSubTypes.Type(value = FlightState.CancelledFlight::class, name = "cancelled")
)
sealed interface FlightState {
    fun evolve(event: FlightEvent) : FlightState {
        return when (event) {
            is FlightEvent.FlightScheduledEvent -> ScheduledFlight(event.flightId, event.origin, event.destination)
            is FlightEvent.FlightDelayedEvent -> {
                when (this) {
                    is ScheduledFlight -> DelayedFlight(this.flightId, this.origin, this.destination,
                        listOf(DelayReason.fromEvent(event)))
                    is DelayedFlight -> DelayedFlight(this.flightId, this.origin, this.destination,
                        this.delayReasons + DelayReason.fromEvent(event))
                    else -> this
                }
            }
            is FlightEvent.FlightCancelledEvent -> {
                if (this is ScheduledFlight || this is DelayedFlight) {
                    CancelledFlight(this.flightId, event.reason)
                } else {
                    this
                }
            }
        }
    }

    val flightId: String
    data class EmptyFlight(override val flightId: String) : FlightState
    data class ScheduledFlight(override val flightId: String, val origin: String, val destination: String) : FlightState
    data class DelayedFlight(override val flightId: String, val origin: String, val destination: String, val delayReasons: List<DelayReason>) : FlightState
    data class CancelledFlight(override val flightId: String, val cancelledReason: String) : FlightState

    data class DelayReason (val reason: String, val timestamp: Long) {
        companion object {
            fun fromEvent(event: FlightEvent.FlightDelayedEvent): DelayReason {
                return DelayReason(event.reason, System.currentTimeMillis())
            }
        }
    }

    companion object {
        val Empty = EmptyFlight("")
    }
}

sealed interface FlightQuery {
    data class GetFlightDetailsQuery(val flightId: String) : FlightQuery
    data class GetAllFlightsQuery(val flightId: String) : FlightQuery
    data class FlightsByDestination(val destination: String) : FlightQuery
    data class FlightsByOrigin(val origin: String) : FlightQuery
}

@Serdeable
data class FlightsListResponse(
    val flights: List<String>
)

@Serdeable
data class FlightDetailsResponse(
    val flightId: String,
    val flightNumber: String,
    val status: String,
    val origin: String? = null,
    val destination: String? = null,
    val cancelReason: String? = null,
    val delayReasons: List<String> = emptyList()
)