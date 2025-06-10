# Aggregates

I can think of 4 options for the decider pattern with Axon and Micronaut:

## Option 1: Just use an evolve method for events

[FlightAggregateOption1.kt](./FlightAggregateOption1.kt)

Downs side are you don't get compile time help for exhaustive pattern matching on the commands, however you do for the events. pros are it's quite simple to setup and leans on the library to do the work. Also don't need to wrap the commands in a CommandMessage.

It seems you can use the inherited type for the event handler, which means you can write it once for all events. Yay.

## Option 2: Sum Type for Commands and Events

When dispatching a command, instead of using the default concreate command when sending instead wrap it in a commandmessage as follows:

```kotlin
private fun <T> sendCommandAsSumType(command: T, clazz: Class<T>): String {
		val commandMessage = GenericCommandMessage(GenericMessage<T?>(command, MetaData.emptyInstance()), clazz.name)
		val result: Any = commandGateway.sendAndWait(commandMessage)
		return result.toString()
	}
```

This option is a compromise TBH and feels like the balance is about right?

[FlightAggregateOption2.kt](./FlightAggregateOption2.kt)

## Option 3: Explicit Decider and DeciderAggregate to split the concerns

Cleanest option IMO, if you want to have a pure decider.

[FlightAggregateOption3.kt](./FlightAggregateOption3.kt)

## Option 4: Decider and DeciderAggregate combined

Like option 3, but with the decider and aggregate combined into one class, mixing concerns.

[FlightAggregateOption4.kt](./FlightAggregateOption4.kt)