package com.playground

import com.playground.autowire.ApplicationConfigurer
import com.playground.projections.CancelledFlightsCounterProjection
import com.playground.projections.FlightDetailsInlineProjection
import com.playground.projections.ScheduledFlightsByDestinationProjection
import com.playground.projections.ScheduledFlightsByOriginProjection
import com.playground.queries.AllFlightsQueryHandler
import com.playground.queries.FlightDetailsQueryHandler
import com.playground.queries.FlightsByDestinationQueryHandler
import com.playground.queries.FlightsByOriginQueryHandler
import io.micronaut.context.annotation.Bean
import org.axonframework.config.Configurer
import org.axonframework.eventhandling.PropagatingErrorHandler

@Bean
class FlightApplicationConfigurer(private val allFlightsQueryHandler: AllFlightsQueryHandler,
                                  private val flightDetailsQueryHandler: FlightDetailsQueryHandler,
                                  private val flightsByOriginQueryHandler: FlightsByOriginQueryHandler,
                                  private val flightsByDestinationQueryHandler: FlightsByDestinationQueryHandler,
                                  private val scheduledFlightsByOriginProjection: ScheduledFlightsByOriginProjection,
                                  private val scheduledFlightsByDestinationProjection: ScheduledFlightsByDestinationProjection,
                                  private val inLineProjection: FlightDetailsInlineProjection,
                                  private val cancelledFlightsCounterProjection: CancelledFlightsCounterProjection,) : ApplicationConfigurer {
    override fun configure(configurer: Configurer): Configurer {
        return configurer.registerQueryHandler { allFlightsQueryHandler }
            .registerQueryHandler { flightDetailsQueryHandler }
            .registerQueryHandler { flightsByOriginQueryHandler }
            .registerQueryHandler { flightsByDestinationQueryHandler }
            .eventProcessing { epConfig ->
                epConfig.registerSubscribingEventProcessor(FlightDetailsInlineProjection.NAME)
                .registerEventHandler { scheduledFlightsByOriginProjection }
                .registerEventHandler { scheduledFlightsByDestinationProjection }
                .registerEventHandler { cancelledFlightsCounterProjection }
                .registerEventHandler { inLineProjection }
                .registerListenerInvocationErrorHandler(FlightDetailsInlineProjection.NAME) { PropagatingErrorHandler.INSTANCE }
                .registerSaga(FlightManagementSaga::class.java)
            }
    }
}