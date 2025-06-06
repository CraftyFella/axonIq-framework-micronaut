package com.playground.autowire

import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.Transaction
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.slf4j.LoggerFactory
import java.sql.Connection

@Singleton
class UnitOfWorkAwareTransactionManager(private val connectionProvider: ConnectionProvider) : TransactionManager {

	override fun startTransaction(): Transaction {

		return if (!CurrentUnitOfWork.isStarted() || CurrentUnitOfWork.get().phase()
				.isAfter(UnitOfWork.Phase.PREPARE_COMMIT)
		) {
			val connection = connectionProvider.connection
			JdbcConnectionTransaction(connection)
		}
		else {
			val connection = connectionProvider.connection
			connection.autoCommit = false
			NoTransaction()
		}
	}

	private class NoTransaction : Transaction {
		override fun commit() {}
		override fun rollback() {}
	}

	private class JdbcConnectionTransaction(private val connection: Connection) : Transaction {

		companion object {
			private val logger = LoggerFactory.getLogger(JdbcConnectionTransaction::class.java)
		}

		@Volatile
		private var isCompleted = false

		override fun commit() {
			if (isCompleted) {
				logger.warn("Attempting to commit already completed transaction")
				return
			}
			try {
				connection.commit()
			} finally {
				cleanup()
			}
		}

		override fun rollback() {
			if (isCompleted) {
				logger.warn("Attempting to rollback already completed transaction")
				return
			}

			try {
				connection.rollback()
			} finally {
				cleanup()
			}
		}

		private fun cleanup() {
			try {
				if (!connection.isClosed) {
					connection.close()
				}
			}
			finally {
				isCompleted = true
			}
		}
	}
}

