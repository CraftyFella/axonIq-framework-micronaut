package com.playground.autowire

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.DataSourceConnectionProvider
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper
import javax.sql.DataSource

@Factory
class ConnectionFactory {

	@Singleton
	fun dataSource(): DataSource {
		val config = HikariConfig().apply {
			jdbcUrl = "jdbc:postgresql://localhost:5432/axon_eventstore"
			username = "postgres"
			password = "password"
			driverClassName = "org.postgresql.Driver"

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
		UnitOfWorkAwareConnectionProviderWrapper(
			DataSourceConnectionProvider(dataSource)
		)
}
