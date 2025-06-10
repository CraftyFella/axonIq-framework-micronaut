package com.playground.queries

import com.playground.FlightQuery
import jakarta.inject.Singleton
import org.axonframework.common.jdbc.ConnectionProvider
import org.axonframework.queryhandling.QueryHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

@Singleton
class FlightDetailsQueryHandler(private val connectionProvider: ConnectionProvider) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(FlightDetailsQueryHandler::class.java)
    }

    @QueryHandler
    fun handle(query: FlightQuery.GetFlightDetailsQuery): FlightDetailsResponse {
        log.debug("Handling GetFlightDetailsQuery for flight ${query.flightId}")

        connectionProvider.connection.use { connection ->
            // First get the main flight details
            val flightSql = "SELECT * FROM flights WHERE id = ?"

            connection.prepareStatement(flightSql).use { ps ->
                ps.setString(1, query.flightId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val flightId = rs.getString("id")
                        val status = rs.getString("status")
                        val origin = rs.getString("origin")
                        val destination = rs.getString("destination")
                        val cancelReason = rs.getString("cancelled_reason")

                        // Now get the delay reasons from the flight_delays table
                        val delayReasons = if (status == "DELAYED" || rs.getInt("delay_count") > 0) {
                            getDelayReasons(connection, flightId)
                        } else {
                            emptyList()
                        }

                        return FlightDetailsResponse(
                            flightId = flightId,
                            status = status,
                            origin = origin,
                            destination = destination,
                            cancelReason = cancelReason,
                            delayReasons = delayReasons
                        )
                    }

                    return FlightDetailsResponse(
                        flightId = query.flightId,
                        status = "NOT_FOUND"
                    )
                }
            }
        }
    }

    private fun getDelayReasons(connection: Connection, flightId: String): List<String> {
        val delaySql = "SELECT reason FROM flight_delays WHERE flight_id = ? ORDER BY delay_time"
        val delayReasons = mutableListOf<String>()

        connection.prepareStatement(delaySql).use { ps ->
            ps.setString(1, flightId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val reason = rs.getString("reason")
                    if (!reason.isNullOrBlank()) {
                        delayReasons.add(reason)
                    }
                }
            }
        }

        return delayReasons
    }
}