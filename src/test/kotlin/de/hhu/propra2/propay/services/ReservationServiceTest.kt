package de.hhu.propra2.propay.services

import de.hhu.propra2.propay.entities.Account
import de.hhu.propra2.propay.entities.Reservation
import de.hhu.propra2.propay.exceptions.AttemptedRobberyException
import de.hhu.propra2.propay.exceptions.InsufficientFundsException
import de.hhu.propra2.propay.exceptions.NiceTryException
import de.hhu.propra2.propay.exceptions.ReservationNotFoundException
import de.hhu.propra2.propay.repositories.ReservationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import java.util.*
import kotlin.collections.ArrayList

class ReservationServiceTest {

    private lateinit var accountService: AccountService
    private lateinit var reservationRepository: ReservationRepository

    private lateinit var testReservation: Reservation

    private lateinit var acc1: Account
    private lateinit var acc2: Account

    private val reservationAmount: Double = 199.0

    @Before
    fun setup() {
        accountService = mock(AccountService::class.java)
        reservationRepository = mock(ReservationRepository::class.java)

        acc1 = Account(1, "User 1", .0)
        acc2 = Account(2, "User 2", .0)

        testReservation = Reservation(199, reservationAmount, acc2)

        Mockito.`when`(accountService.getAccount("User 1")).thenReturn(acc1)
        Mockito.`when`(accountService.getAccount("User 2")).thenReturn(acc2)
    }

    @Test(expected = AttemptedRobberyException::class)
    fun reserveNegativeAmount() {
        val reservationService = ReservationService(accountService, reservationRepository)

        reservationService.reserve("User 1", "User 2", -1.0)
    }

    @Test(expected = InsufficientFundsException::class)
    fun reserveWithoutCoverage() {
        val reservationService = ReservationService(accountService, reservationRepository)

        reservationService.reserve("User 1", "User 2", 1.0)
    }

    @Test(expected = NiceTryException::class)
    fun reserveWithSameSourceAndTarget() {
        val reservationService = ReservationService(accountService, reservationRepository)

        reservationService.reserve("User 1", "User 1", 1.0)
    }

    @Test
    fun testReservation() {
        val reservationService = ReservationService(accountService, reservationRepository)

        acc1.amount = reservationAmount * 2
        acc1.reservations = ArrayList()

        Mockito
                .`when`(reservationRepository.save(
                        Reservation(null, reservationAmount, acc2)))
                .thenReturn(testReservation)

        reservationService.reserve(
                "User 1",
                "User 2", reservationAmount)

        assertTrue(acc1.reservations.contains(testReservation))

    }

    @Test(expected = ReservationNotFoundException::class)
    fun releaseNonExistentReservation() {
        val reservationService = ReservationService(accountService, reservationRepository)

        Mockito.`when`(reservationRepository.findById(1)).thenReturn(Optional.empty())

        reservationService.releaseReservation("User 1", 1)
    }

    @Test
    fun testRelease() {
        val reservationService = ReservationService(accountService, reservationRepository)
        Mockito.`when`(reservationRepository.findById(1)).thenReturn(Optional.of(testReservation))

        acc1.reservations = listOf(testReservation).toMutableList()

        reservationService.releaseReservation("User 1", 1)

        assertEquals(0, acc1.reservations.size)
        assertEquals(0.0, acc1.amount, 0.1)
    }

    @Test
    fun punishAndTransferReservation() {
        val reservationService = ReservationService(accountService, reservationRepository)

        acc1.amount = 200.0
        acc1.reservations = listOf(testReservation).toMutableList()

        acc2.amount = 0.0

        val intermediateAccount = Account(11, acc1.account, acc1.amount, listOf(testReservation).toMutableList())
        val resultAccount = Account(111, acc1.account, acc1.amount)
        resultAccount.reservations = Collections.emptyList();

        Mockito.`when`(reservationRepository.findById(testReservation.id!!))
                .thenReturn(Optional.of(testReservation))
        Mockito.`when`(accountService.move(acc1, acc2, testReservation.amount))
                .thenReturn(Pair(intermediateAccount, acc2))
        Mockito.`when`(accountService.save(intermediateAccount))
                .thenReturn(resultAccount)


        val result = reservationService.punishAndTransferReservation("User 1", testReservation.id!!)


        // input is not mutated
        assertEquals(1, acc1.reservations.size)
        // resulting account is also empty
        assertEquals(0, result.reservations.size)
        // result account is third version of acc1
        assertEquals(resultAccount.id, result.id)

        Mockito.verify(accountService, times(1))
                .move(acc1, acc2, reservationAmount)

        Mockito.verify(accountService, times(1))
                .save(intermediateAccount)


    }

    @Test(expected = ReservationNotFoundException::class)
    fun punishAndTransferNonExistentReservation() {
        val reservationService = ReservationService(accountService, reservationRepository)

        Mockito.`when`(reservationRepository.findById(1)).thenReturn(Optional.empty())

        reservationService.punishAndTransferReservation("User 1", 1)
    }

    @Test
    fun punishAndTransferReservationExactlyRightAmountOfFunds() {
        val reservationService = ReservationService(accountService, reservationRepository)

        val intermediateAccount = Account(11, acc1.account, acc1.amount, listOf(testReservation).toMutableList())
        val resultAccount = Account(111, acc1.account, acc1.amount)

        Mockito.`when`(reservationRepository.findById(1))
                .thenReturn(Optional.of(testReservation))
        Mockito.`when`(accountService.move(acc1, acc2, testReservation.amount))
                .thenReturn(Pair(intermediateAccount, acc2))
        Mockito.`when`(accountService.save(intermediateAccount))
                .thenReturn(resultAccount)

        acc1.reservations = listOf(testReservation).toMutableList()
        acc1.amount = reservationAmount

        val result = reservationService.punishAndTransferReservation("User 1", 1)

        assertEquals(0, result.reservations.size)
        assertEquals(0.0, result.amount, 0.1)
    }
}