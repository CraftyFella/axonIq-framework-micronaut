# Axon Framework Aggregate Patterns

This document explores four different implementation patterns for event-sourced aggregates using Axon Framework with Micronaut. Each pattern represents a different approach to implementing the decider pattern.

## Option 1: Axon Command Handlers with Event Evolution

This option uses Axon's built-in command handling capabilities, where each command corresponds to a method in the aggregate class with the @CommandHandler annotation. 
The aggregate state is managed directly within the aggregate class, and events are published as a result of command handling.
The state is evolved by applying events to the aggregate.

[FlightAggregateOption1.kt](./FlightAggregateOption1.kt)

### Pros:
- Simplicity: Straightforward implementation that follows Axon's core patterns
- Less boilerplate: Minimal code required to get started
- Direct dependency injection support: Easy to inject dependencies using Micronaut
- Natural command handling: Commands map directly to handler methods

### Cons:
- No compile-time exhaustiveness checks for command handlers
- Less explicit decision-making process compared to pure functional approaches

## Option 2: Sum Types for Commands and Events

This option and the remaining options uses the sum type for commands, which requires a custom command dispatching mechanism to preserve the sum type information.

see [SumTypeCommandDispatcher.kt](../SumTypeCommandDispatcher.kt) for the custom command dispatcher implementation.

The main goal here is to embrace the compile-time exhaustiveness checks for command handlers

[FlightAggregateOption2.kt](./FlightAggregateOption2.kt)

### Pros:
- Single entry point for all commands via the `handle(command: FlightCommand)` method
- Compile-time exhaustiveness checks for command handlers
- Maintains the Axon programming model while gaining some functional benefits

### Cons:
- Requires special command dispatching to preserve sum type information
- More complex structure compared to Option 1
- Still mixes the aggregate state management with decision logic

## Option 3: Pure Decider Pattern with Separation of Concerns

This is the most complex solution, however some of the boilerplate would be re-used across multiple aggregates. The goal here is to have a pure decider using the decider interface.

see [Decider.kt](./Decider.kt) 

[FlightAggregateOption3.kt](./FlightAggregateOption3.kt)

### Pros:
- Clean separation between pure decision logic and effects
- Decider is completely framework-agnostic and easily testable
- Generic base class allows reuse across multiple aggregates
- Most closely follows functional domain modeling principles
- Easily extractable business logic for reuse in other contexts

### Cons:
- Most complex implementation with multiple layers of abstraction
- Requires understanding of generics and abstract class patterns
- Some duplication between decider and aggregate class definitions
- May be overkill for simpler domain models

## Option 4: Hybrid Decider-Aggregate Pattern

This is like option 3, but it moves the decider and aggregate into a single class.

[FlightAggregateOption4.kt](./FlightAggregateOption4.kt)

### Pros:
- Cleaner than Option 2 with better separation of concerns
- Less complex than Option 3 while maintaining most benefits
- Centralizes decision logic while keeping framework integration clean
- Provides a consistent pattern that scales well with complexity
- Good balance between functional purity and practical implementation

### Cons:
- Still mixes some concerns in a single class
- Abstract base class adds some complexity
- Requires more boilerplate than Option 1
- Not as purely functional as Option 3

## Choosing an Approach

The appropriate pattern depends on several factors:

- **Team Experience**: Options 1 and 2 may be easier for teams new to DDD and event sourcing
- **Project Complexity**: Options 3 and 4 scale better for complex domains
- **Testing Requirements**: Options 3 and 4 offer better testability of business logic
- **Performance Concerns**: Option 1 has the least overhead, while Option 3 has the most
- **Maintenance**: Options 3 and 4 provide better separation of concerns for long-term maintenance

For most projects, Option 4 represents a good balance between functional purity and practical implementation.