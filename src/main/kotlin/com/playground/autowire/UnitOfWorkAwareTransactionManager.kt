package com.playground.autowire

import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.transaction.Transaction
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.messaging.unitofwork.UnitOfWork

@Singleton
class UnitOfWorkAwareTransactionManager(private val connectionProvider: ConnectionProvider) : TransactionManager {

	override fun startTransaction(): Transaction {

		val isInActiveUnitOfWork = CurrentUnitOfWork.isStarted() && !CurrentUnitOfWork.get().phase()
			.isAfter(UnitOfWork.Phase.PREPARE_COMMIT)

		if (isInActiveUnitOfWork) {
            val connection = connectionProvider.connection
            connection.autoCommit = false
        }

		return NoTransaction()
	}

	private class NoTransaction : Transaction {
		override fun commit() {}
		override fun rollback() {}
	}
}

