package com.playground.library

import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.ErrorContext
import org.axonframework.eventhandling.ErrorHandler
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.PropagatingErrorHandler
import org.axonframework.eventhandling.async.SequentialPerAggregatePolicy
import org.axonframework.eventhandling.deadletter.jdbc.GenericDeadLetterTableFactory
import org.axonframework.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue
import org.axonframework.messaging.deadletter.GenericDeadLetter
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue
import org.axonframework.serialization.Serializer
import org.slf4j.LoggerFactory


@Singleton
class DeadLetterQueueFactory(
    private val transactionManager: TransactionManager,
    private val serializer: Serializer,
    private val connectionProvider: ConnectionProvider
) {

    private val logger = LoggerFactory.getLogger(DeadLetterQueueFactory::class.java)

    fun create(
        processingGroup: String,
        maxSequences: Int = 100,
        maxSequenceSize: Int = 1000
    ): SequencedDeadLetterQueue<EventMessage<*>> {
        val queue = JdbcSequencedDeadLetterQueue.builder<EventMessage<*>>()
            .processingGroup(processingGroup)
            .maxSequences(maxSequences)
            .maxSequenceSize(maxSequenceSize)
            .eventSerializer(serializer)
            .genericSerializer(serializer)
            .transactionManager(transactionManager)
            .connectionProvider(connectionProvider)
            .build()

        safelyCreateSchema(queue, processingGroup)
        return queue
    }

    private fun safelyCreateSchema(queue: JdbcSequencedDeadLetterQueue<EventMessage<*>>, processingGroup: String) {

        try {
            queue.createSchema(PostgresDeadLetterTableFactory())
            logger.info("Dead letter queue schema created successfully for processing group: $processingGroup")
        } catch (e: Exception) {
            if (e.message?.contains("already exists") == true ||
                e.cause?.message?.contains("already exists") == true
            ) {
                logger.info("Dead letter queue schema already exists for processing group: $processingGroup")
            } else {
                logger.error("Failed to create dead letter queue schema", e)
                throw e
            }
        }
    }

    class PostgresDeadLetterTableFactory : GenericDeadLetterTableFactory() {
        override fun serializedDataType(): String {
            return "BYTEA"
        }
    }
}


// This is how Axon wants to to work with DQL, this is also compatible with Axon Console
// Main issue is that is will commit the UOW of the original event handler which is NOT ideal.
fun EventProcessingConfigurer.registerAxonDeadLetterQueueHandling(
    deadLetterQueueFactory: DeadLetterQueueFactory,
    processingGroup: String,
    maxSequences: Int = 100,
    maxSequenceSize: Int = 1000
): EventProcessingConfigurer {
    return this
        .registerDeadLetterQueue(processingGroup) {
            deadLetterQueueFactory.create(
                processingGroup = processingGroup,
                maxSequences = maxSequences,
                maxSequenceSize = maxSequenceSize
            )
        }
}

// This is how I want to work with DQL, it should be an exception handler, that way the original UOW is rolled back and we start a new one to sent to the DQL.
// Could make this use more of the logic from the DeadLetteringEventHandlerInvoker class possibly.
// Ohe issue with this method is that as well as NOT working with Axon Console, it will try to process streams which are already corrupt, so you'd have to couple it with a
// message handler which checks to see if the steam is already in the DQL.
fun EventProcessingConfigurer.registerDeadLetterQueueUsingErrorHandler(
    deadLetterQueueFactory: DeadLetterQueueFactory,
    processingGroup: String,
    maxSequences: Int = 100,
    maxSequenceSize: Int = 1000
): EventProcessingConfigurer {
    return this
        .registerListenerInvocationErrorHandler(processingGroup) { PropagatingErrorHandler.INSTANCE }
        .registerErrorHandler(processingGroup) {
            DeadLetterQueueErrorHandler(
                deadLetterQueueFactory.create(
                    processingGroup = processingGroup,
                    maxSequences = maxSequences,
                    maxSequenceSize = maxSequenceSize
                )
            )
        }
}


class DeadLetterQueueErrorHandler(
    private val deadLetterQueue: SequencedDeadLetterQueue<EventMessage<*>>,
    private val maxRetries: Int = 10
) : ErrorHandler {

    private val logger = LoggerFactory.getLogger(DeadLetterQueueErrorHandler::class.java)

    private fun unwrapException(exception: Throwable): Throwable {
        return exception
    }

    override fun handleError(errorContext: ErrorContext) {
        val cause = unwrapException(errorContext.error())
        val failedEvents = errorContext.failedEvents()
        val processorName = errorContext.eventProcessor()

        logger.error(
            "Error handling events in processor [{}]",
            processorName,
            cause
        )

        if (failedEvents.isEmpty()) {
            logger.warn("No events to send to dead letter queue")
            return
        }

        for (event in failedEvents) {
            try {

                val sequenceIdentifier =
                    SequentialPerAggregatePolicy.instance().getSequenceIdentifierFor(event) ?: event.identifier

                val letter = GenericDeadLetter(sequenceIdentifier, event, cause)

                deadLetterQueue.enqueue(sequenceIdentifier, letter)
                logger.info("Event sent to dead letter queue: {}", event.identifier)
            } catch (e: Exception) {
                logger.error("Failed to enqueue message to dead letter queue: {}", event.identifier, e)
            }
        }
    }
}