package com.ximedes.conto.service

import com.ximedes.conto.db.AccountMapper
import com.ximedes.conto.domain.*
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val FIRST_ACCOUNT_DESCRIPTION = "Checking"

@Service
@Transactional
class AccountService(private val accountMapper: AccountMapper,
                     private val userService: UserService,
                     private val eventPublisher: ApplicationEventPublisher) {

    private val logger = KotlinLogging.logger { }

    // This property will be set when an admin user is created,
    // which should happen on startup
    lateinit var rootAccount: Account

    @EventListener
    fun onAdminUserCreated(event: AdminUserCreatedEvent) {
        rootAccount = doCreateAccount(event.adminUsername, "Bank", Long.MIN_VALUE, 0L)
    }

    @EventListener
    fun onUserSignedUp(event: UserSignedUpEvent) {
        val a = doCreateAccount(event.username, FIRST_ACCOUNT_DESCRIPTION, 0L, 0L)
        eventPublisher.publishEvent(FirstAccountCreatedEvent(this, a.owner, a.accountID))
    }

    fun findByAccountID(accountID: String?) = accountMapper.find(AccountCriteria(null, accountID)).firstOrNull()

    fun findAllAccounts(): List<Account> = accountMapper.find(AccountCriteria())

    @PreAuthorize("hasRole('ROLE_USER')")
    fun createAccount(description: String): Account {
        val username = userService.loggedInUser!!.username
        return doCreateAccount(username, description, 0L, 0L)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun createAccount(ownerName: String, description: String, minimumBalance: Long): Account {
        // Make sure the user exists before creating the account
        val owner = userService.findByUsername(ownerName) ?: throw UsernameNotFoundException(ownerName)
        return doCreateAccount(owner.username, description, minimumBalance, 0L)
    }

    private fun doCreateAccount(owner: String, description: String, minimumBalance: Long, balance: Long): Account {
        val accountID = generateAccountID()
        val account = Account(accountID, owner, description, minimumBalance, balance)
        accountMapper.insertAccount(account)
        logger.info("Created new account $account.")
        return account
    }


    private fun generateAccountID(): String {
        val numericID = accountMapper.getNextUniqueID()
        val accountIDUniquePart = numericID.toString().padStart(8, '0')
        return "NLBRAT$accountIDUniquePart"
    }

    fun findByOwner(user: String) = accountMapper.find(AccountCriteria(ownerID = user))

    @EventListener
    @Synchronized fun onTransferCreated(event: TransferCreatedEvent) {
        val debitAccount = findByAccountID(event.debitAccountID)
            ?: throw AccountNotAvailableException(AccountNotAvailableException.Type.DEBIT, "Debit account with ID ${event.debitAccountID} not found.")
        accountMapper.updateAccount(debitAccount.copy(balance = debitAccount.balance - event.amount))
        logger.info { "Debit account ${event.debitAccountID} transferred ${event.amount} updated with ${debitAccount.balance - event.amount}" }
        val creditAccount = findByAccountID(event.creditAccountID)
            ?: throw AccountNotAvailableException(AccountNotAvailableException.Type.CREDIT, "Credit account with ID ${event.creditAccountID} not found.")
        accountMapper.updateAccount(creditAccount.copy(balance = creditAccount.balance + event.amount))
        logger.info { "Credit account ${event.creditAccountID} transferred ${event.amount} updated with ${creditAccount.balance + event.amount}" }
    }
}