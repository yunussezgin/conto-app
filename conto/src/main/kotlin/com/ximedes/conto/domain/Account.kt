package com.ximedes.conto.domain

data class Account(val accountID: String, val owner: String, val description: String, val minimumBalance: Long, val totalBalance: Long)

data class AccountCriteria(val ownerID: String? = null, val accountID: String? = null)