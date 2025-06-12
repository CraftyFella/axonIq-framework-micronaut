package com.playground.queries

import com.playground.FlightQuery
import com.playground.FlightsListResponse
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.queryhandling.QueryHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class AllFlightsQueryHandler(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(AllFlightsQueryHandler::class.java)
    }

    @QueryHandler
    fun handle(query: FlightQuery.GetAllFlightsQuery): FlightsListResponse {
        log.debug("Handling GetAllFlightsQuery")

        val sql = "SELECT id FROM flights LIMIT 200"
        val result = mutableListOf<String>()

        connectionProvider.connection.use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(rs.getString("id"))
                    }
                    return FlightsListResponse(result)
                }
            }
        }
    }
}