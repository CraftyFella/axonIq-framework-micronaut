package com.playground.library

import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.EventProcessingConfigurer
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.deadletter.jdbc.GenericDeadLetterTableFactory
import org.axonframework.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.deadletter.DeadLetter
import org.axonframework.messaging.deadletter.Decisions
import org.axonframework.messaging.deadletter.EnqueueDecision
import org.axonframework.messaging.deadletter.EnqueuePolicy
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.serialization.Serializer
import org.slf4j.LoggerFactory
import java.util.function.Function


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
    return this
        .registerDeadLetterPolicy(processingGroup) { EnqueueOnlyIfException() }
        .registerDeadLetterQueue(processingGroup) {
        deadLetterQueueFactory.create(
            processingGroup = processingGroup,
            maxSequences = maxSequences,
            maxSequenceSize = maxSequenceSize
        )
    }
        .registerHandlerInterceptor(processingGroup) { UnitOfWorkAwareDeadLetterMessageHandlerInterceptor() }
}

// This class won't get called as the DQL stuff takes over completely. It would be nice if it did work.
class UnitOfWorkAwareDeadLetterMessageHandlerInterceptor : MessageHandlerInterceptor<EventMessage<*>> {

    override fun handle(unitOfWork: UnitOfWork<out EventMessage<*>>,
                        interceptorChain: InterceptorChain): Any? {
        try {
            return interceptorChain.proceed()
        } catch (e: Exception) {
            unitOfWork.rollback()
            throw e
        }
    }
}

class EnqueueOnlyIfException : EnqueuePolicy<EventMessage<*>?> {
    override fun decide(
        letter: DeadLetter<out EventMessage<*>?>,
        cause: Throwable?
    ): EnqueueDecision<EventMessage<*>?> {
        if (cause == null) {
            return Decisions.doNotEnqueue<EventMessage<*>?>()
        }
        val retries = letter.diagnostics().getOrDefault("retries", -1) as Int
        if (retries < 10) {
            // Let's continue and increase retries:
            return Decisions.requeue<EventMessage<*>?>(
                cause
            ) { l: DeadLetter<out EventMessage<*>?>? -> l!!.diagnostics().and("retries", retries + 1) }
        }
        return Decisions.enqueue<EventMessage<*>?>(cause)
    }
}