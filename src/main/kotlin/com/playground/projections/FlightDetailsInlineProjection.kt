package com.playground.projections

import com.playground.FlightEvent
import io.micronaut.tracing.annotation.NewSpan
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Types

@Singleton
@ProcessingGroup(FlightDetailsInlineProjection.NAME)
open class FlightDetailsInlineProjection(private val connectionProvider: ConnectionProvider) {

    companion object {
        const val NAME = "inline-projection"
    }

    private val logger = LoggerFactory.getLogger(FlightDetailsInlineProjection::class.java)

    init {
        createTablesIfNotExists()
    }

    private fun createTablesIfNotExists() {
        val flightTableSql = """
            CREATE TABLE IF NOT EXISTS flights (
                id VARCHAR(255) PRIMARY KEY,
                flight_number VARCHAR(50),
                origin VARCHAR(100),
                destination VARCHAR(100),
                delay_count INT DEFAULT 0,
                cancelled BOOLEAN DEFAULT FALSE,
                status VARCHAR(20) DEFAULT 'SCHEDULED',
                cancelled_reason TEXT,
                version BIGINT DEFAULT 0
            )
        """

        val delayTableSql = """
            CREATE TABLE IF NOT EXISTS flight_delays (
                id SERIAL PRIMARY KEY,
                flight_id VARCHAR(255) REFERENCES flights(id),
                reason TEXT,
                delay_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """

        connectionProvider.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(flightTableSql)
                statement.execute(delayTableSql)
            }
        }
    }

    @EventHandler
    fun on(event: FlightEvent.FlightScheduledEvent) {
        logger.debug("Projection3 Flight scheduled with id: ${event.flightId}")
        insertFlight(event.flightId, event.flightNumber, event.origin, event.destination, "SCHEDULED")
    }

    private fun insertFlight(flightId: String, flightNumber: String, origin: String, destination: String, status: String) {
        val sql = """
    INSERT INTO flights (id, flight_number, origin, destination, delay_count, cancelled, status, version)
    VALUES (?, ?, ?, ?, 0, false, ?, 0)
    ON CONFLICT (id) DO NOTHING
"""

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, flightId)
                ps.setString(2, flightNumber)
                ps.setString(3, origin)
                ps.setString(4, destination)
                ps.setString(5, status)
                ps.executeUpdate()
                logger.debug("Flight {} inserted with number: {}, origin: {}, destination: {}, status: {}",
                    flightId, flightNumber, origin, destination, status)
            }
        }
    }

    @EventHandler
    @NewSpan
    open fun on(event: FlightEvent.FlightDelayedEvent) {
        logger.debug("Projection3 Flight delayed with id: ${event.flightId}")

        connectionProvider.connection.use { connection ->
            // Add the delay record
            addDelayRecord(connection, event.flightId, event.reason ?: "No reason provided")

            // Update the flight status
            val flightDetails = getFlightDetails(connection, event.flightId)
                ?: FlightDetails(0, false, 0, "", "", "")

            updateFlight(
                connection,
                event.flightId,
                flightDetails.delayCount + 1,
                flightDetails.cancelled,
                "DELAYED",
                null,
                0
            )
        }
    }

    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        if (event.flightId == "breaksprojection2") {
            throw RuntimeException("Projection3 Flight cancelled with id: ${event.flightId}")
        }
        logger.debug("Projection3 Flight cancelled with id: ${event.flightId}")

        connectionProvider.connection.use { connection ->
            val flightDetails = getFlightDetails(connection, event.flightId)
                ?: FlightDetails(0, false, 0, "", "", "")

            updateFlight(
                connection,
                event.flightId,
                flightDetails.delayCount,
                true,
                "CANCELLED",
                event.reason,
                0
            )
        }
    }

    private fun getFlightDetails(connection: Connection, flightId: String): FlightDetails? {
        val sql = "SELECT delay_count, cancelled, version, origin, destination, flight_number FROM flights WHERE id = ?"

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, flightId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    return FlightDetails(
                        delayCount = rs.getInt("delay_count"),
                        cancelled = rs.getBoolean("cancelled"),
                        version = rs.getLong("version"),
                        origin = rs.getString("origin"),
                        destination = rs.getString("destination"),
                        flightNumber = rs.getString("flight_number")
                    )
                }
            }
        }
        return null
    }

    data class FlightDetails(
        val delayCount: Int,
        val cancelled: Boolean,
        val version: Long,
        val origin: String,
        val destination: String,
        val flightNumber: String
    )


    private fun updateFlight(
        connection: Connection,
        flightId: String,
        delayCount: Int,
        cancelled: Boolean,
        status: String,
        cancelledReason: String?,
        version: Long
    ) {
        val sql = """
        UPDATE flights 
        SET delay_count = ?,
            cancelled = ?,
            status = ?,
            cancelled_reason = ?,
            version = ?
        WHERE id = ?
    """

        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, delayCount)
            ps.setBoolean(2, cancelled)
            ps.setString(3, status)
            if (cancelledReason != null) {
                ps.setString(4, cancelledReason)
            } else {
                ps.setNull(4, Types.VARCHAR)
            }
            ps.setLong(5, version)
            ps.setString(6, flightId)

            ps.executeUpdate()
            logger.debug(
                "Flight {} updated: delays={}, cancelled={}, status={}, version={}",
                flightId, delayCount, cancelled, status, version
            )
        }
    }

    private fun addDelayRecord(connection: Connection, flightId: String, reason: String) {
        val sql = """
        INSERT INTO flight_delays (flight_id, reason)
        VALUES (?, ?)
    """

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, flightId)
            ps.setString(2, reason)
            ps.executeUpdate()
            logger.debug("Delay record added for flight {}: {}", flightId, reason)
        }
    }

}