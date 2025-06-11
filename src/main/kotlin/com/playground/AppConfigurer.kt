package com.playground

import com.playground.aggregate.FlightAggregateOption3
import com.playground.library.ApplicationConfigurer
import com.playground.library.MicronautAggregateConfigurer
import com.playground.projections.CancelledFlightsCounterProjection
import com.playground.projections.FlightDetailsInlineProjection
import com.playground.projections.ScheduledFlightsByDestinationProjection
import com.playground.projections.ScheduledFlightsByOriginProjection
import com.playground.queries.AllFlightsQueryHandler
import com.playground.queries.FlightDetailsQueryHandler
import com.playground.queries.FlightsByDestinationQueryHandler
import com.playground.queries.FlightsByOriginQueryHandler
import jakarta.inject.Singleton
import org.axonframework.config.Configurer
import org.axonframework.eventhandling.PropagatingErrorHandler

@Singleton
class FlightApplicationConfigurer(
    private val allFlightsQueryHandler: AllFlightsQueryHandler,
    private val flightDetailsQueryHandler: FlightDetailsQueryHandler,
    private val flightsByOriginQueryHandler: FlightsByOriginQueryHandler,
    private val flightsByDestinationQueryHandler: FlightsByDestinationQueryHandler,
    private val scheduledFlightsByOriginProjection: ScheduledFlightsByOriginProjection,
    private val scheduledFlightsByDestinationProjection: ScheduledFlightsByDestinationProjection,
    private val flightDetailsInlineProjection: FlightDetailsInlineProjection,
    private val cancelledFlightsCounterProjection: CancelledFlightsCounterProjection,
    private val aggregateFactoryHelper: MicronautAggregateConfigurer,
) : ApplicationConfigurer {
    override fun configure(configurer: Configurer): Configurer {
        return configurer
            .configureAggregate(aggregateFactoryHelper.configurationFor(FlightAggregateOption3::class.java))
            .registerQueryHandler { allFlightsQueryHandler }
            .registerQueryHandler { flightDetailsQueryHandler }
            .registerQueryHandler { flightsByOriginQueryHandler }
            .registerQueryHandler { flightsByDestinationQueryHandler }
            .eventProcessing { processingConfigurer ->
                processingConfigurer
                    .registerSubscribingEventProcessor(FlightDetailsInlineProjection.NAME)
                    .registerListenerInvocationErrorHandler(FlightDetailsInlineProjection.NAME) { PropagatingErrorHandler.INSTANCE }
                    .registerEventHandler { scheduledFlightsByOriginProjection }
                    .registerEventHandler { scheduledFlightsByDestinationProjection }
                    .registerEventHandler { cancelledFlightsCounterProjection }
                    .registerEventHandler { flightDetailsInlineProjection }
                    .registerSaga(FlightManagementSaga::class.java)
            }
    }
}