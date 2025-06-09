package com.playground

import com.playground.InLineProjection.Companion.NAME
import io.micronaut.tracing.annotation.NewSpan
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.slf4j.LoggerFactory
import java.sql.Connection

@Singleton
@ProcessingGroup(NAME)
open class InLineProjection(private val connectionProvider: ConnectionProvider) {

    companion object {
        const val NAME = "inline-projection"
    }

    private val logger = LoggerFactory.getLogger(InLineProjection::class.java)

    init {
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS flights (
                id VARCHAR(255) PRIMARY KEY,
                delay_count INT DEFAULT 0,
                cancelled BOOLEAN DEFAULT FALSE,
                version BIGINT DEFAULT 0
            )
        """

        connectionProvider.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(sql)
            }
        }
    }

    @EventHandler
    fun on(event: FlightEvent.FlightScheduledEvent) {
        logger.debug("Projection3 Flight scheduled with id: ${event.flightId}")
        upsertFlight(event.flightId, 0, false, 0)

    }

    @EventHandler
    @NewSpan
    open fun on(event: FlightEvent.FlightDelayedEvent) {
        logger.debug("Projection3 Flight delayed with id: ${event.flightId}")


        // First get the current delay count
        val currentValues = getFlightDetails(connectionProvider.connection, event.flightId) ?: Triple(0, false, 0)
        val (currentDelayCount, isCancelled, _) = currentValues

        // Increment the delay count
        upsertFlight(event.flightId, currentDelayCount + 1, isCancelled, 0)

    }


    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        if (event.flightId == "breaksprojection2") {
            throw RuntimeException("Projection3 Flight cancelled with id: ${event.flightId}")
        }
        logger.debug("Projection3 Flight cancelled with id: ${event.flightId}")

        //transactionManager.executeInTransaction {

        // Get the current delay count
        val currentValues = getFlightDetails(connectionProvider.connection, event.flightId) ?: Triple(0, false, 0)
        val (currentDelayCount, _, _) = currentValues

        // Mark as cancelled while preserving delay count
        upsertFlight(event.flightId, currentDelayCount, true, 0)
        //}
        throw RuntimeException("Projection3 Flight cancelled with id: ${event.flightId}")

    }


    private fun getFlightDetails(connection: Connection, flightId: String): Triple<Int, Boolean, Long>? {
        val sql = "SELECT delay_count, cancelled, version FROM flights WHERE id = ?"

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, flightId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    val delayCount = rs.getInt("delay_count")
                    val cancelled = rs.getBoolean("cancelled")
                    val version = rs.getLong("version")
                    return Triple(delayCount, cancelled, version)
                }
            }
        }
        return null
    }

    private fun upsertFlight(flightId: String, delayCount: Int, cancelled: Boolean, version: Long) {
        val sql = """
        INSERT INTO flights (id, delay_count, cancelled, version)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (id)
        DO UPDATE SET
            delay_count = ?,
            cancelled = ?,
            version = ?
    """

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                // Parameters for INSERT
                ps.setString(1, flightId)
                ps.setInt(2, delayCount)
                ps.setBoolean(3, cancelled)
                ps.setLong(4, version)

                // Parameters for UPDATE (on conflict)
                ps.setInt(5, delayCount)
                ps.setBoolean(6, cancelled)
                ps.setLong(7, version)

                ps.executeUpdate()
                logger.debug(
                    "Flight {} updated: delays={}, cancelled={}, version={}",
                    flightId, delayCount, cancelled, version
                )
            }
        }
    }

}