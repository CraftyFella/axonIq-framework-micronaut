package com.playground

import com.playground.aggregate.FlightAggregateOption3
import com.playground.library.ApplicationConfigurer
import com.playground.library.DeadLetterQueueFactory
import com.playground.library.MicronautAggregateConfigurer
import com.playground.library.registerAggregateUsingConfigurer
import com.playground.library.registerDeadLetterQueueUsingFactory
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
import org.axonframework.eventhandling.ListenerInvocationErrorHandler
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.axonframework.eventhandling.TrackingEventProcessorConfiguration
import org.axonframework.eventhandling.async.SequentialPolicy
import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker
import java.time.Duration

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
    private val aggregateConfigurer: MicronautAggregateConfigurer,
    private val deadLetterQueueFactory: DeadLetterQueueFactory
) : ApplicationConfigurer {
    override fun configure(configurer: Configurer): Configurer {
        return configurer
            .registerAggregateUsingConfigurer(aggregateConfigurer, FlightAggregateOption3::class.java)
            .registerQueryHandler { allFlightsQueryHandler }
            .registerQueryHandler { flightDetailsQueryHandler }
            .registerQueryHandler { flightsByOriginQueryHandler }
            .registerQueryHandler { flightsByDestinationQueryHandler }
            .eventProcessing { processingConfigurer ->
                processingConfigurer
                    //.registerDefaultListenerInvocationErrorHandler { PropagatingErrorHandler.INSTANCE }
                    // inline projection
                    .registerSubscribingEventProcessor(FlightDetailsInlineProjection.NAME)
                    .registerListenerInvocationErrorHandler(FlightDetailsInlineProjection.NAME) { PropagatingErrorHandler.INSTANCE }
                    .registerEventHandler { flightDetailsInlineProjection }
                    // Asynchronous projections
                    .registerListenerInvocationErrorHandler(ScheduledFlightsByDestinationProjection.NAME) { PropagatingErrorHandler.INSTANCE }
                    .registerTrackingEventProcessorConfiguration(ScheduledFlightsByDestinationProjection.NAME) {
                        TrackingEventProcessorConfiguration
                            .forParallelProcessing(2)
                            .andInitialSegmentsCount(2)
                    }
                    .registerDeadLetterQueueUsingFactory(
                        deadLetterQueueFactory = deadLetterQueueFactory,
                        processingGroup = ScheduledFlightsByDestinationProjection.NAME
                    )
                    .registerEventHandler { scheduledFlightsByOriginProjection }
                    .registerEventHandler { scheduledFlightsByDestinationProjection }
                    .registerEventHandler { cancelledFlightsCounterProjection }
                    .registerSaga(FlightManagementSaga::class.java)
            }
    }
}