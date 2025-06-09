package com.playground

import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler

@Singleton
@ProcessingGroup("ScheduledFlightsByOrigin")
class ScheduledFlightsByOriginProjection(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(ScheduledFlightsByOriginProjection::class.java)
    }

    init {
        createTableIfNotExists()
    }

    private fun createTableIfNotExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS flights_by_origin (
                flight_id VARCHAR(255),
                origin VARCHAR(255),
                PRIMARY KEY (flight_id, origin)
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
        log.debug("OriginProjection: Flight ${event.flightId} scheduled from ${event.origin}")
        insertFlight(event.flightId, event.origin)
    }

    @EventHandler
    fun on(event: FlightEvent.FlightCancelledEvent) {
        log.debug("OriginProjection: Flight ${event.flightId} cancelled")
        removeFlight(event.flightId)
    }

    private fun insertFlight(flightId: String, origin: String) {
        val sql = """
            INSERT INTO flights_by_origin (flight_id, origin)
            VALUES (?, ?)
            ON CONFLICT (flight_id, origin) DO NOTHING
        """

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, flightId)
                ps.setString(2, origin)
                ps.executeUpdate()
                log.debug("Added flight $flightId to origin $origin")
            }
        }
    }

    private fun removeFlight(flightId: String) {
        val sql = "DELETE FROM flights_by_origin WHERE flight_id = ?"

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, flightId)
                ps.executeUpdate()
                log.debug("Removed flight $flightId from origins table")
            }
        }
    }
}

@Singleton
@ProcessingGroup("ScheduledFlightsByDestination")
class ScheduledFlightsByDestinationProjection(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(ScheduledFlightsByDestinationProjection::class.java)
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

