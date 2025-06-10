package com.playground.queries

import com.playground.FlightQuery
import com.playground.FlightsListResponse
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.queryhandling.QueryHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class FlightsByDestinationQueryHandler(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(FlightsByDestinationQueryHandler::class.java)
    }

    @QueryHandler
    fun handle(query: FlightQuery.FlightsByDestination): FlightsListResponse {
        log.debug("Handling FlightsByDestination query for destination ${query.destination}")

        val sql = "SELECT flight_id FROM flights_by_destination WHERE destination = ?"
        val result = mutableListOf<String>()

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, query.destination)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(rs.getString("flight_id"))
                    }
                    return FlightsListResponse(result)
                }
            }
        }
    }
}