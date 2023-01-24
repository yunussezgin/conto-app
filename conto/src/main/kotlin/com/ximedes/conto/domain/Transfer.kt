package com.ximedes.conto.domain

data class Transfer(val transferID: Long,
                    val debitAccountID: String,
                    val creditAccountID: String,
                    val amount: Long,
                    val description: String) {

    constructor(debitAccountID: String, creditAccountID: String, amount: Long, description: String) : this(-1L, debitAccountID, creditAccountID, amount, description)
}