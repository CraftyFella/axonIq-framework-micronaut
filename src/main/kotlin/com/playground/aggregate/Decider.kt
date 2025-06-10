package com.playground.aggregate

interface Decider<TState, TCommand, TEvent> {
    fun decide(state: TState, command: TCommand): List<TEvent>
    fun evolve(state: TState, event: TEvent): TState
    fun initialState(): TState
    fun streamId(event: TEvent): String
}