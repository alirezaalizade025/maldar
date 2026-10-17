package com.personalfinance.tracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import com.personalfinance.tracker.ui.screens.showTransactionDatePicker
import com.personalfinance.tracker.ui.screens.showTransactionTimePicker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TransactionFormsTest {
    @Test fun choosingDate_keepsExistingHourAndMinute() {
        val original = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 17, 18, 45, 0); set(Calendar.MILLISECOND, 0) }
        var saved = original.timeInMillis
        showTransactionDatePicker(RuntimeEnvironment.getApplication(), saved) { saved = it }
        val dialog = ShadowDialog.getLatestDialog() as DatePickerDialog
        assertTrue(dialog.isShowing)
        dialog.datePicker.updateDate(2026, Calendar.OCTOBER, 20)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val selected = Calendar.getInstance().apply { timeInMillis = saved }
        assertEquals(20, selected.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, selected.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, selected.get(Calendar.MINUTE))
    }
    @Test fun choosingTime_keepsExistingDate() {
        val original = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 17, 18, 45, 0); set(Calendar.MILLISECOND, 0) }
        var saved = original.timeInMillis
        showTransactionTimePicker(RuntimeEnvironment.getApplication(), saved) { saved = it }
        val dialog = ShadowDialog.getLatestDialog() as TimePickerDialog
        assertTrue(dialog.isShowing)
        val picker = dialog.findViewById<android.widget.TimePicker>(android.content.res.Resources.getSystem().getIdentifier("timePicker", "id", "android"))
        picker.hour = 9; picker.minute = 15
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val selected = Calendar.getInstance().apply { timeInMillis = saved }
        assertEquals(17, selected.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, selected.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, selected.get(Calendar.MINUTE))
    }
}
