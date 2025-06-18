package com.playground.library

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.DataSourceConnectionProvider
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper
import org.axonframework.common.transaction.NoTransactionManager
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.messaging.unitofwork.UnitOfWork
import java.sql.Connection
import javax.sql.DataSource

@ConfigurationProperties("db")
class DbConfig {
    var url: String? = "jdbc:postgresql://localhost:5432/axon_eventstore"
    var username: String? = "postgres"
    var password: String = "password"
    var driver: String = "org.postgresql.Driver"
}

@Factory
class UnitOfWorkAwareConnectionProviderFactory {

    @Singleton
    fun transactionManager(): TransactionManager = NoTransactionManager.INSTANCE

    @Singleton
    fun dataSource(dbConfig: DbConfig): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = dbConfig.url
            username = dbConfig.username
            password = dbConfig.password
            driverClassName = dbConfig.driver

            // Connection pool settings
            maximumPoolSize = 20
            minimumIdle = 5
            connectionTimeout = 30000 // 30 seconds
            idleTimeout = 600000 // 10 minutes
            maxLifetime = 1800000 // 30 minutes
            leakDetectionThreshold = 60000 // 60 seconds

            // PostgreSQL specific optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")

            // Pool name for monitoring
            poolName = "AxonHikariPool"

            // Register JMX MBeans for monitoring
            isRegisterMbeans = true
        }

        return HikariDataSource(config)
    }

    @Singleton
    fun connectionProvider(dataSource: DataSource): ConnectionProvider =
        UnitOfWorkAwareAutoCommitConnectionProvider(
            UnitOfWorkAwareConnectionProviderWrapper(
                DataSourceConnectionProvider(dataSource)
            )
        )

    class UnitOfWorkAwareAutoCommitConnectionProvider(private val unitOfWorkAwareConnectionProviderWrapper: UnitOfWorkAwareConnectionProviderWrapper) :
        ConnectionProvider {
        override fun getConnection(): Connection? {
            val connection = unitOfWorkAwareConnectionProviderWrapper.connection

            val isInActiveUnitOfWork = CurrentUnitOfWork.isStarted() && !CurrentUnitOfWork.get().phase().isAfter(
                UnitOfWork.Phase.PREPARE_COMMIT
            )
            if (isInActiveUnitOfWork) {
                connection.autoCommit = false
            }
            return connection
        }

    }
}
