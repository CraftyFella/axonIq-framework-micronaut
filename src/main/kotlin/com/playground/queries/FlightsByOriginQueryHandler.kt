package com.playground.queries

import com.playground.FlightQuery
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.queryhandling.QueryHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class FlightsByOriginQueryHandler(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(FlightsByOriginQueryHandler::class.java)
    }

    @QueryHandler
    fun handle(query: FlightQuery.FlightsByOrigin): FlightsListResponse {
        log.debug("Handling FlightsByOrigin query for origin ${query.origin}")

        val sql = "SELECT flight_id FROM flights_by_origin WHERE origin = ?"
        val result = mutableListOf<String>()

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, query.origin)
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


