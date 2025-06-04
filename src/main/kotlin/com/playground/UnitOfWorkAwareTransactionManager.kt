package com.playground

import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.Transaction
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException


class UnitOfWorkAwareTransactionManager(private val connectionProvider: ConnectionProvider) : TransactionManager {

	override fun startTransaction(): Transaction {
		return try {
			val connection = connectionProvider.connection
			connection.autoCommit = false
			if (CurrentUnitOfWork.isStarted()) {
				NoTransaction()
			} else {
				JdbcConnectionTransaction(connection)
			}
		} catch (e: SQLException) {
			throw RuntimeException("Failed to start transaction", e)
		}
	}

}

private class NoTransaction : Transaction {

	override fun commit() {
		// No operation, as the Rollback and Commit are controlled by the unit of work
	}

	override fun rollback() {
		// No operation, as the Rollback and Commit are controlled by the unit of work
	}

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
		} catch (e: SQLException) {
			logger.warn("Error during connection cleanup", e)
		}
		finally {
			isCompleted = true
		}
	}
}