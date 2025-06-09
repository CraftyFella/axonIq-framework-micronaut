## Axon with Micronaut and Postgres

### Done

* ~~Try out Live projections (use event store to read events directly)~~
* ~~subscribing event processor (same thread)~~
* ~~Streaming event processor (background thread)~~
* ~~Try out snapshots~~
* ~~Try out sagas~~
* ~~Transactions~~
* ~~Micronaut Aggregate Factory~~
* ~~Micronaut Saga Factory~~
* ~~Fix connection leak, after 20 requires the pool is exhausted, check the~~
* ~~Decider concepts~~

### To Do

* Have IO in the streaming projections
   * Scheduled Flights by Origin
   * Scheduled Flights by Destination
   * Flights and their Status
* Multi node projections (leadership)
* Scale out projections (Sharding projections)
* Replay projections (streaming)
* Load Test
* Transaction Manager playing nicely with Micronaut data (Nice to have, can work around this TBH)
