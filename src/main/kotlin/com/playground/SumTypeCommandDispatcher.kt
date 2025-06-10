package com.playground

import jakarta.inject.Singleton
import org.axonframework.commandhandling.GenericCommandMessage
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.messaging.GenericMessage
import org.axonframework.messaging.MetaData

@Singleton
class SumTypeCommandDispatcher(private val commandGateway: CommandGateway) {

	fun <T> sendCommandAsSumType(command: T, clazz: Class<T>): String {
		val commandMessage = GenericCommandMessage(GenericMessage<T?>(command, MetaData.emptyInstance()), clazz.name)
		val result: Any = commandGateway.sendAndWait(commandMessage)
		return result.toString()
	}
}