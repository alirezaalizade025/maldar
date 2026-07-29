package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.data.SmsSenderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsAccountMatcherTest {
    private val accounts = listOf(
        BankAccountEntity(1, "بانک", "اول", "1234"),
        BankAccountEntity(2, "بانک", "دوم", "9876")
    )
    private val senders = listOf(
        SmsSenderEntity(1, "BANK", 1),
        SmsSenderEntity(2, "BANK", 2)
    )

    @Test
    fun sharedSender_isSeparatedByCardLastFour() {
        val matched = SmsAccountMatcher.match("BANK", "برداشت از کارت ۱۲۳۴", senders, accounts)
        assertEquals(1L, matched?.bankAccountId)
    }

    @Test
    fun sharedSender_withoutCardSuffix_isRejectedAsAmbiguous() {
        assertNull(SmsAccountMatcher.match("BANK", "برداشت انجام شد", senders, accounts))
    }
}
