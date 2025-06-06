package com.playground.autowire

import com.playground.AysncProjecitonWithStandardProcessingGroup
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.common.jdbc.DataSourceConnectionProvider
import org.axonframework.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper
import java.sql.Array
import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.NClob
import java.sql.PreparedStatement
import java.sql.SQLWarning
import java.sql.SQLXML
import java.sql.Savepoint
import java.sql.ShardingKey
import java.sql.Statement
import java.sql.Struct
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
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

		LoggingConnectionProvider(
			UnitOfWorkAwareConnectionProviderWrapper(
				DataSourceConnectionProvider(dataSource)
			)
		)

}


class LoggingConnectionProvider(private val delegate: ConnectionProvider): ConnectionProvider {
	companion object {
		val log = org.slf4j.LoggerFactory.getLogger(LoggingConnectionProvider::class.java)
		private val CONNECTIONS = ConcurrentHashMap<Int, ConnectionInfo>()

		init {
			// Start a background thread to dump unclosed connections every 10 seconds
			Thread {
				while (true) {
					dumpUnclosedConnections()
					Thread.sleep(10000) // 10 seconds
				}
			}.apply {
				isDaemon = true
				name = "Connection-Leak-Detector"
				start()
			}
		}

		private fun dumpUnclosedConnections() {
			if (CONNECTIONS.isNotEmpty()) {
				log.debug("=== UNCLOSED CONNECTIONS: ${CONNECTIONS.size} ===")
				CONNECTIONS.forEach { (id, info) ->
					log.debug("Connection $id (type: ${info.connectionType}) open for ${(System.currentTimeMillis() - info.createdAt) / 1000}s - Created at:")
					info.stackTrace.forEach { trace ->
						log.debug("  at $trace")
					}
				}
				log.debug("=====================================")
			} else {
				log.debug("=== No Unclosed connection ===")
			}
		}
	}

	data class ConnectionInfo(
		val createdAt: Long = System.currentTimeMillis(),
		val connectionType: String = "unknown",
		val stackTrace: List<String> = Thread.currentThread().stackTrace
			.drop(3)  // Skip the first 3 elements (internal calls)
			.take(10) // Take only 10 elements
			.map { it.toString() }
	)

	override fun getConnection(): Connection? {
		val connection = delegate.connection ?: return null
		return TrackedConnection(connection)
	}

	class TrackedConnection(private val connection: Connection) : Connection by connection {
		private val connectionId = System.identityHashCode(connection)

		init {
			// Store connection in tracking map with stack trace and type information
			CONNECTIONS[connectionId] = ConnectionInfo(
				connectionType = connection.javaClass.name
			)
		}

		override fun close() {
			// Remove from tracking when closed
			CONNECTIONS.remove(connectionId)
			connection.close()
		}

		override fun beginRequest() {
			connection.beginRequest()
		}

		override fun endRequest() {
			connection.endRequest()
		}

		override fun setShardingKeyIfValid(
			shardingKey: ShardingKey?,
			superShardingKey: ShardingKey?,
			timeout: Int
		): Boolean {
			return connection.setShardingKeyIfValid(shardingKey, superShardingKey, timeout)
		}

		override fun setShardingKeyIfValid(shardingKey: ShardingKey?, timeout: Int): Boolean {
			return connection.setShardingKeyIfValid(shardingKey, timeout)
		}

		override fun setShardingKey(shardingKey: ShardingKey?, superShardingKey: ShardingKey?) {
			connection.setShardingKey(shardingKey, superShardingKey)
		}

		override fun setShardingKey(shardingKey: ShardingKey?) {
			connection.setShardingKey(shardingKey)
		}
	}
}
