package com.playground.projections

import com.playground.FlightEvent
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
@ProcessingGroup(ScheduledFlightsByDestinationProjection.NAME)
class ScheduledFlightsByDestinationProjection(private val connectionProvider: ConnectionProvider) {

    companion object {
        const val NAME = "ScheduledFlightsByDestination"
        val log: Logger = LoggerFactory.getLogger(ScheduledFlightsByDestinationProjection::class.java)
    }

    init {
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS flights_by_destination (
                flight_id VARCHAR(255),
                destination VARCHAR(255),
                PRIMARY KEY (flight_id, destination)
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
        log.debug("DestinationProjection: Flight ${event.flightId} scheduled to ${event.destination}")
        insertFlight(event.flightId, event.destination)
        if (event.flightId.startsWith("bang")) {
            throw IllegalArgumentException("Flight ID cannot start with 'bang'")
        }
    }

    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        log.debug("DestinationProjection: Flight ${event.flightId} cancelled")
        removeFlight(event.flightId)
    }

    private fun insertFlight(flightId: String, destination: String) {
        val sql = """
            INSERT INTO flights_by_destination (flight_id, destination)
            VALUES (?, ?)
            ON CONFLICT (flight_id, destination) DO NOTHING
        """

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, flightId)
                ps.setString(2, destination)
                ps.executeUpdate()
                log.debug("Added flight $flightId to destination $destination")
            }
        }
    }

    private fun removeFlight(flightId: String) {
        val sql = "DELETE FROM flights_by_destination WHERE flight_id = ?"

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, flightId)
                ps.executeUpdate()
                log.debug("Removed flight $flightId from destinations table")
            }
        }
    }
}

