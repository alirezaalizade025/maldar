package com.personalfinance.tracker.sms

import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.data.SmsSenderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun amountContainingAnotherAccountsDigits_doesNotSelectThatAccount() {
        val body = "برداشت 123400 ریال از کارت 6037-****-****-9876"
        assertEquals(2L, SmsAccountMatcher.match("BANK", body, senders, accounts)?.bankAccountId)
        assertFalse(SmsAccountMatcher.bodyMatchesLast4(body, "1234"))
    }

    @Test
    fun melliMessage_usesAccountSuffixInsteadOfBalance() {
        val body = "بانك ملي ايران\nبرداشت:5,870,000-\nحساب:85000\nمانده:5,913,480\n0817-18:29"
        val melliAccounts = listOf(
            accounts[0].copy(accountLast4 = "3480"),
            accounts[1].copy(accountLast4 = "5000")
        )
        assertEquals(2L, SmsAccountMatcher.match("BANK", body, senders, melliAccounts)?.bankAccountId)
        assertTrue(SmsAccountMatcher.bodyMatchesLast4(body, "۵۰۰۰"))
        assertFalse(SmsAccountMatcher.bodyMatchesLast4(body, "3480"))
    }

    @Test
    fun unrelatedNumbers_areNotJoinedOrMatched() {
        for (body in listOf("مبلغ 12 ریال ساعت 34", "مبلغ 1234 ریال", "پیگیری 991234")) {
            assertNull(SmsAccountMatcher.match("BANK", body, senders, accounts))
            assertFalse(SmsAccountMatcher.bodyMatchesLast4(body, "1234"))
        }
    }

    @Test
    fun identifiers_matchOnlyTheirLastFourDigits() {
        for (body in listOf("کارت 123456789876", "حساب 1234.56.9876", "کارت 1234****")) {
            assertFalse(SmsAccountMatcher.bodyMatchesLast4(body, "1234"))
        }
    }

    @Test
    fun identifiers_supportPersianArabicAndMaskedFormats() {
        for (body in listOf(
            "حساب: ۰۱۲.۳۴۵.۱۲۳۴", "شماره کارت: ٦٠٣٧-٩٩٠٠-٠٠٠٠-١٢٣٤",
            "سپرده 1234", "برداشت از *۱۲۳۴", "Card: 6037 9900 0000 1234",
            "کارت:\n۱۲۳۴"
        )) {
            assertTrue(body, SmsAccountMatcher.bodyMatchesLast4(body, "۱۲۳۴"))
        }
    }

    @Test
    fun multipleMatchingAccounts_areRejectedAsAmbiguous() {
        val body = "انتقال از کارت 1234 به کارت 9876"
        assertNull(SmsAccountMatcher.match("BANK", body, senders, accounts))
        assertNull(SmsAccountMatcher.match("BANK", "کارت 1234", senders,
            accounts.map { it.copy(accountLast4 = "1234") }))
    }

    @Test
    fun accountWithoutSuffix_preservesSingleAccountFallback() {
        val account = accounts.first().copy(accountLast4 = "")
        assertEquals(1L, SmsAccountMatcher.match("BANK", "برداشت انجام شد",
            senders.take(1), listOf(account))?.bankAccountId)
        assertTrue(SmsAccountMatcher.bodyMatchesLast4("برداشت انجام شد", ""))
    }
}
