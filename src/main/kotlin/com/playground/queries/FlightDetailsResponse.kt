package com.playground.queries

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class FlightDetailsResponse(
    val flightId: String,
    val status: String,
    val origin: String? = null,
    val destination: String? = null,
    val cancelReason: String? = null,
    val delayReasons: List<String> = emptyList()
)