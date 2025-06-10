package com.playground.queries

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class FlightsListResponse(
    val flights: List<String>
)