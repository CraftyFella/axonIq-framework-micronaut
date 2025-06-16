package com.playground.library

import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue
import org.axonframework.serialization.Serializer
import jakarta.inject.Singleton
import org.axonframework.eventhandling.deadletter.jdbc.GenericDeadLetterTableFactory
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
                e.cause?.message?.contains("already exists") == true) {
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

// Extension function for EventProcessingConfigurer
fun EventProcessingConfigurer.registerDeadLetterQueueUsingFactory(
    deadLetterQueueFactory: DeadLetterQueueFactory,
    processingGroup: String,
    maxSequences: Int = 100,
    maxSequenceSize: Int = 1000
): EventProcessingConfigurer {
    return this.registerDeadLetterQueue(processingGroup) {
        deadLetterQueueFactory.create(
            processingGroup = processingGroup,
            maxSequences = maxSequences,
            maxSequenceSize = maxSequenceSize
        )
    }
}