package com.ximedes.conto.service

import com.nhaarman.mockitokotlin2.*
import com.ximedes.conto.AccountBuilder
import com.ximedes.conto.TransferBuilder
import com.ximedes.conto.UserBuilder
import com.ximedes.conto.db.TransferMapper
import com.ximedes.conto.domain.*
import com.ximedes.conto.domain.AccountNotAvailableException.Type.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class TransferServiceTest {

    val userService = mock<UserService>()
    val accountService = mock<AccountService>()
    val transferMapper = mock<TransferMapper>()
    val publisher = mock<ApplicationEventPublisher>()

    val transferService = TransferService(userService, accountService, transferMapper, publisher)
    val transferCaptor = argumentCaptor<Transfer>()

    @Test
    fun `transfer from a user's own account with sufficient balance succeed, fail with insufficient balance`() {
        val user = UserBuilder.build()
        whenever(userService.loggedInUser).thenReturn(user)
        whenever(userService.findByUsername(user.username)).thenReturn(user)

        // Balance is 150, so should succeed
        val debitAccount = AccountBuilder.build {
            owner = user.username
            balance = 150L
        }
        val creditAccount = AccountBuilder.build()
        whenever(accountService.findByAccountID(debitAccount.accountID)).thenReturn(debitAccount)
        whenever(accountService.findByAccountID(creditAccount.accountID)).thenReturn(creditAccount)

        val t = transferService.attemptTransfer(debitAccount.accountID, creditAccount.accountID, 100, "desc")
        verify(transferMapper).insertTransfer(t)

        assertEquals(debitAccount.accountID, t.debitAccountID)
        assertEquals(creditAccount.accountID, t.creditAccountID)
        assertEquals(100, t.amount)
        assertEquals("desc", t.description)

        // Balance is 50, so should fail
        val updatedDebitAccount = debitAccount.copy(balance = 50)
        whenever(accountService.findByAccountID(debitAccount.accountID)).thenReturn(updatedDebitAccount)

        assertThrows<InsufficientFundsException> {
            transferService.attemptTransfer(debitAccount.accountID, creditAccount.accountID, 100, "desc")
        }
    }

    @Test
    fun `a regular user may not transfer money from accounts not owned by them`() {
        val user = UserBuilder.build {
            username = "someone"
        }
        val debitAccount = AccountBuilder.build {
            owner = "someone_else"
        }

        val creditAccount = AccountBuilder.build()

        whenever(userService.loggedInUser).thenReturn(user)
        whenever(userService.findByUsername(user.username)).thenReturn(user)
        whenever(accountService.findByAccountID(debitAccount.accountID)).thenReturn(debitAccount)
        whenever(accountService.findByAccountID(creditAccount.accountID)).thenReturn(creditAccount)

        assertThrows<IllegalArgumentException> {
            transferService.attemptTransfer(debitAccount.accountID, creditAccount.accountID, 1, "desc")
        }
    }

    @Test
    fun `an admin user may transfer money from an account not owned by them`() {
        val admin = UserBuilder.build {
            username = "admin"
            role = Role.ADMIN
        }

        // Balance is 50 on debit account
        val debitAccount = AccountBuilder.build {
            owner = "not_admin"
            balance = 50
        }

        val creditAccount = AccountBuilder.build()

        whenever(userService.loggedInUser).thenReturn(admin)
        whenever(userService.findByUsername(admin.username)).thenReturn(admin)
        whenever(accountService.findByAccountID(debitAccount.accountID)).thenReturn(debitAccount)
        whenever(accountService.findByAccountID(creditAccount.accountID)).thenReturn(creditAccount)

        val t = transferService.attemptTransfer(debitAccount.accountID, creditAccount.accountID, 10, "desc")
        verify(transferMapper).insertTransfer(t)

        assertEquals(debitAccount.accountID, t.debitAccountID)
        assertEquals(creditAccount.accountID, t.creditAccountID)
        assertEquals(10, t.amount)
        assertEquals("desc", t.description)
    }



    @Test
    fun `findTransfersByAccountID finds transfers by account ID`() {
        val user = UserBuilder.build()
        whenever(userService.loggedInUser).thenReturn(user)
        val account = AccountBuilder.build {
            owner = user.username
        }

        val l = listOf(TransferBuilder.build {
            debitAccountID = account.accountID
        })
        whenever(accountService.findByAccountID(account.accountID)).thenReturn(account)
        whenever(transferMapper.findTransfersByAccountID(account.accountID)).thenReturn(l)
        val t = transferService.findTransfersByAccountID(account.accountID)
        assertSame(t, l)
    }

    @Test
    fun `only owners and admins can get the list of transfer for an account`() {
        val userA = UserBuilder.build()
        val account = AccountBuilder.build {
            owner = userA.username
        }
        whenever(accountService.findByAccountID(account.accountID)).thenReturn(account)

        val userB = UserBuilder.build()
        whenever(userService.loggedInUser).thenReturn(userB)
        assertThrows<IllegalArgumentException> {
            transferService.findTransfersByAccountID(account.accountID)
        }

        val admin = UserBuilder.build {
            username = "admin"
            role = Role.ADMIN
        }
        whenever(userService.loggedInUser).thenReturn(admin)
        assertTrue(transferService.findTransfersByAccountID(account.accountID).isEmpty())
    }

    @Test
    fun `on receiving a FirstAccountCreatedEvent the account is credited with the signup bonus from the root account`() {
        whenever(accountService.rootAccount).thenReturn(AccountBuilder.build { accountID = "root" })
        transferService.onFirstAccountCreated(FirstAccountCreatedEvent(this, "owner", "account"))
        verify(transferMapper).insertTransfer(transferCaptor.capture())
        val t = transferCaptor.firstValue
        assertEquals(SIGNUP_BONUS, t.amount)
        assertEquals("root", t.debitAccountID)
        assertEquals("account", t.creditAccountID)
    }


    @Test
    fun `it throws an exception when using a non-existing account`() {
        whenever(accountService.findByAccountID("foo")).thenReturn(null)
        whenever(accountService.findByAccountID("bar")).thenReturn(AccountBuilder.build { accountID = "bar" })

        assertThrows<AccountNotAvailableException> {
            transferService.attemptTransfer("foo", "bar", 1, "desc")
        }.also { assertEquals(DEBIT, it.type) }

        assertThrows<AccountNotAvailableException> {
            transferService.attemptTransfer("bar", "foo", 1, "desc")
        }.also { assertEquals(CREDIT, it.type) }

        assertThrows<AccountNotAvailableException> {
            transferService.findTransfersByAccountID("foo")
        }.also { assertEquals(UNKNOWN, it.type) }
    }

}