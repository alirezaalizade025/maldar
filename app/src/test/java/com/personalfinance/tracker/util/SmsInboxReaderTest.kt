package com.personalfinance.tracker.util

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SmsInboxReaderTest {
    class Inbox : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            args: Array<out String>?, sort: String?): Cursor = MatrixCursor(arrayOf("body", "address", "date")).apply {
            addRow(arrayOf("برداشت 50000 ریال", "BANK", 200L))
            addRow(arrayOf("برداشت 10000 ریال مانده 900000 ریال", "BANK", 100L))
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
    }
    @Test fun balanceRefresh_skipsMessagesWithoutRemainingBalance() {
        ShadowContentResolver.registerProviderInternal("sms", Inbox())
        val result = SmsInboxReader.lastSmsForSenders(RuntimeEnvironment.getApplication(), listOf("BANK"))
        assertEquals(90000.0, result.amount!!, 0.0)
        assertEquals(100L, result.timestampMillis)
    }
}
