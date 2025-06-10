package com.playground.projections

import com.playground.FlightEvent
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
@ProcessingGroup("CancelledFlightsCounter")
class CancelledFlightsCounterProjection(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(CancelledFlightsCounterProjection::class.java)
    }

    init {
        createTableIfNotExists()
        initializeCounterIfNeeded()
    }

    private fun createTableIfNotExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS cancelled_flights_counter (
                id VARCHAR(255) DEFAULT 'singleton',
                counter INTEGER,
                PRIMARY KEY (id)
            )
        """

        connectionProvider.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
        }
    }

    private fun initializeCounterIfNeeded() {
        val sql = """
            INSERT INTO cancelled_flights_counter (id, counter)
            VALUES ('singleton', 0)
            ON CONFLICT (id) DO NOTHING
        """

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.executeUpdate()
                log.debug("Initialized cancelled flights counter if needed")
            }
        }
    }

    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        log.debug("CancelledFlightsCounter: Flight ${event.flightId} cancelled, incrementing counter")
        incrementCounter()
    }

    private fun incrementCounter() {
        val sql = """
            UPDATE cancelled_flights_counter
            SET counter = counter + 1
            WHERE id = 'singleton'
        """

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                val updatedRows = ps.executeUpdate()
                log.debug("Incremented cancelled flights counter (rows updated: $updatedRows)")
            }
        }
    }

    fun getCurrentCount(): Int {
        val sql = "SELECT counter FROM cancelled_flights_counter WHERE id = 'singleton'"

        return connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                val resultSet = ps.executeQuery()
                if (resultSet.next()) {
                    val count = resultSet.getInt("counter")
                    log.debug("Current cancelled flights count: $count")
                    count
                } else {
                    log.warn("Could not find counter record, returning 0")
                    0
                }
            }
        }
    }
}