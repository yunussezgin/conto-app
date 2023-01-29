package com.ximedes.conto.service

import com.ximedes.conto.db.TransferMapper
import com.ximedes.conto.domain.*
import com.ximedes.conto.domain.AccountNotAvailableException.Type.*
import com.ximedes.conto.sumByLong
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.lang.IllegalStateException
import javax.security.auth.login.AccountNotFoundException

const val SIGNUP_BONUS = 100L;

@Service
@Transactional
class TransferService(
    private val userService: UserService,
    private val accountService: AccountService,
    private val transferMapper: TransferMapper,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = KotlinLogging.logger { }

    // TODO this can go now, right? because these are runtimes?
    @Throws(InsufficientFundsException::class, AccountNotFoundException::class)
    @PreAuthorize("isAuthenticated()")
    fun attemptTransfer(
        debitAccountID: String,
        creditAccountID: String,
        amount: Long,
        description: String
    ): Transfer {
        val debitAccount = accountService.findByAccountID(debitAccountID)
            ?: throw AccountNotAvailableException(DEBIT, "Debit account with ID $debitAccountID not found.")
        val creditAccount = accountService.findByAccountID(creditAccountID)
            ?: throw AccountNotAvailableException(CREDIT, "Credit account with ID $creditAccountID not found.")

        require(userService.loggedInUser.hasAccessTo(debitAccount)) {
            "User ${userService.loggedInUser} does not have access to account $debitAccountID"
        }

        if (debitAccount.balance - amount < debitAccount.minimumBalance) {
            throw InsufficientFundsException("Insufficient funds for transferring $amount from account ${debitAccount.accountID} with balance ${debitAccount.balance}")
        }

        return Transfer(debitAccount.accountID, creditAccount.accountID, amount, description).also {
            transferMapper.insertTransfer(it)
            eventPublisher.publishEvent(TransferCreatedEvent(this, debitAccount.accountID, creditAccount.accountID, amount))
        }

    }

    @PreAuthorize("isAuthenticated()")
    fun findTransfersByAccountID(accountID: String): List<Transfer> {
        val account = accountService.findByAccountID(accountID)
            ?: throw AccountNotAvailableException(UNKNOWN, "Account with ID $accountID not found.")

        require(userService.loggedInUser.hasAccessTo(account)) {
            "User ${userService.loggedInUser} does not have access to account $accountID"
        }
        return transferMapper.findTransfersByAccountID(accountID)
    }

    @EventListener
    fun onFirstAccountCreated(event: FirstAccountCreatedEvent) {
        logger.info { "Granting signup bonus to owner ${event.owner} of new first account ${event.accountID}" }
        val t = Transfer(accountService.rootAccount.accountID, event.accountID, SIGNUP_BONUS, "Welcome to Conto!")
        transferMapper.insertTransfer(t)
        eventPublisher.publishEvent(TransferCreatedEvent(this, accountService.rootAccount.accountID, event.accountID, SIGNUP_BONUS))
    }

}
