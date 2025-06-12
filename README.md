# Axon with Micronaut and Postgres

## Features

### Done

* ~~Try out Live projections (use event store to read events directly)~~
* ~~subscribing event processor (same thread)~~
* ~~Streaming event processor (background thread)~~
* ~~Try out snapshots~~
* ~~Try out sagas~~
* ~~Transactions~~
* ~~Micronaut Aggregate Factory~~
* ~~Micronaut Saga Factory~~
* ~~Fix connection leak~~
* ~~Decider concepts~~
* ~~Have IO in the streaming projections~~
  * ~~Scheduled Flights by Origin~~
  * ~~Scheduled Flights by Destination~~
  * ~~Flights delay count and status~~
* ~~Query handlers for the projections.~~
* ~~Connect to Axon console~~
* ~~Replay projections (streaming)~~
* ~~Multi node projections (leadership)~~
* ~~Scale out projections (Sharding projections)~~
### To Do

* Load Test
* Transaction Manager playing nicely with Micronaut data (Nice to have, can work around this TBH)

## Deciders

see [Aggregate README.md](./src/main/kotlin/com/playground/aggregate/README.md) for more information on the decider pattern with Axon and Micronaut.
