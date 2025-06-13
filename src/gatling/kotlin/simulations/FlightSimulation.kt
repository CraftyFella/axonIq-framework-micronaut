package simulations

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.exec
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.core.CoreDsl.listFeeder
import io.gatling.javaapi.core.CoreDsl.rampUsers
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Session
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.time.Duration
import java.util.function.Function

class FlightsSimulation : Simulation() {

    private val httpProtocol = http
        .baseUrl("http://localhost:8080")
        .acceptHeader("application/json")
        .contentTypeHeader("application/json")

    // Proper feeder using listFeeder
    private val flightFeeder = listFeeder(
        listOf(
            mapOf(
                "origin" to "JFK",
                "destination" to "LHR",
                "delayCount" to 3,
                "shouldCancel" to true
            ),
            mapOf(
                "origin" to "LHR",
                "destination" to "JFK",
                "delayCount" to 2,
                "shouldCancel" to false
            ),
            mapOf(
                "origin" to "LAX",
                "destination" to "SFO",
                "delayCount" to 1,
                "shouldCancel" to true
            ),
            mapOf(
                "origin" to "CDG",
                "destination" to "NRT",
                "delayCount" to 4,
                "shouldCancel" to false
            ),
            mapOf(
                "origin" to "SYD",
                "destination" to "DXB",
                "delayCount" to 2,
                "shouldCancel" to true
            )
        )
    ).circular()

    private val scn = scenario("Flight API Load Test")
        .feed(flightFeeder)
        .exec(
            http("Get All Flights")
                .get("/flights")
                .check(status().`is`(200))
        )
        .pause(1, 3)
        .exec(
            http("Get Flights By Origin")
                .get("/flights/by-origin/#{origin}")
                .check(status().`is`(200))
        )
        .pause(1, 3)
        .exec(
            http("Get Flights By Destination")
                .get("/flights/by-destination/#{destination}")
                .check(status().`is`(200))
        )
        .pause(1, 3)
        .exec(
            http("Schedule New Flight")
                .post("/flights")
                .body(StringBody("{}"))
                .check(status().`is`(201))
                .check(jsonPath("$.flightId").saveAs("flightId"))
        )
        .pause(1, 3)
        // Use repeat with session attribute
        .repeat("#{delayCount}").on(
            exec(
                http("Delay Flight")
                    .patch("/flights/#{flightId}/delay")
                    .body(StringBody("{}"))
                    .check(status().`is`(200))
            )
        )
        // Use doIf with proper Function interface
        .doIf(Function<Session, Boolean> { session ->
            session.getBoolean("shouldCancel") ?: false
        }).then(
            exec(
                http("Cancel Flight")
                    .patch("/flights/#{flightId}/cancel")
                    .body(StringBody("{}"))
                    .check(status().`is`(200))
            )
        )

    init {
        setUp(
            scn.injectOpen(
                rampUsers(200).during(Duration.ofMinutes(1))
            )
        ).protocols(httpProtocol)
    }
}