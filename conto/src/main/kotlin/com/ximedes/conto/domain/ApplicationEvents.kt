package com.ximedes.conto.domain

import org.springframework.context.ApplicationEvent
import java.time.temporal.TemporalAmount
import java.util.Locale

class AdminUserCreatedEvent(source: Any, val adminUsername: String) : ApplicationEvent(source)

class UserSignedUpEvent(source: Any, val username: String) : ApplicationEvent(source)

class FirstAccountCreatedEvent(source: Any, val owner: String, val accountID: String) : ApplicationEvent(source)

class TransferCreatedEvent(source: Any, val debitAccountID: String, val creditAccountID: String, val amount: Long) : ApplicationEvent(source)