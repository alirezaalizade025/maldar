package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

enum class LoanStatusTone { NEUTRAL, POSITIVE, WARNING, NEGATIVE }

@Composable
fun LoanCard(
    title: String,
    remainingAmount: String,
    remainingLabel: String,
    progress: Float,
    progressDescription: String,
    modifier: Modifier = Modifier,
    lender: String? = null,
    originalAmount: String? = null,
    nextDueDate: String? = null,
    remainingInstallments: String? = null,
    status: String? = null,
    statusTone: LoanStatusTone = LoanStatusTone.NEUTRAL,
    linkedAccount: String? = null,
    notes: String? = null,
    paid: Boolean = false,
    onOpenDetails: () -> Unit,
    onPay: (() -> Unit)?,
    onEdit: () -> Unit,
    onReminder: () -> Unit,
    onDelete: () -> Unit,
    detailsDescription: String,
    paymentLabel: String,
    editDescription: String,
    reminderDescription: String,
    deleteDescription: String
) {
    AppCard(modifier = modifier, style = AppCardStyle.RAISED) {
        Column(Modifier.fillMaxWidth()) {
            val summary = listOfNotNull(title, lender, remainingAmount, status, nextDueDate, remainingInstallments).joinToString("، ")
            Column(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenDetails)
                    .semantics { contentDescription = summary }
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        lender?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    status?.let {
                        val (container, content) = when (statusTone) {
                            LoanStatusTone.POSITIVE -> MaldarDesign.colors.positiveContainer to MaldarDesign.colors.positive
                            LoanStatusTone.WARNING -> MaldarDesign.colors.warningContainer to MaldarDesign.colors.onWarning
                            LoanStatusTone.NEGATIVE -> MaldarDesign.colors.negativeContainer to MaldarDesign.colors.negative
                            LoanStatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = container, contentColor = content) {
                            Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(remainingLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(remainingAmount, style = MaterialTheme.typography.titleLarge)
                originalAmount?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                        contentDescription = progressDescription
                    },
                    color = if (paid) MaldarDesign.colors.positive else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                nextDueDate?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                remainingInstallments?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                linkedAccount?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (onPay != null) {
                AppButton(text = paymentLabel, onClick = onPay, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onOpenDetails) { Icon(Icons.Filled.Info, contentDescription = detailsDescription) }
                IconButton(onClick = onReminder) { Icon(Icons.Filled.Notifications, contentDescription = reminderDescription) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = editDescription) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = deleteDescription, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun LoanCardPreview() = MaldarDesignTheme {
    LoanCard(
        title = "وام مسکن", lender = "بانک ملت", remainingAmount = "۱۲٬۵۰۰٬۰۰۰",
        remainingLabel = "مانده",
        originalAmount = "مبلغ اولیه: ۲۰٬۰۰۰٬۰۰۰ تومان", progress = 0.38f,
        progressDescription = "۳۸ درصد پرداخت شده", nextDueDate = "سررسید بعدی: ۱۵ آبان ۱۴۰۳",
        remainingInstallments = "۱۲ قسط باقی‌مانده", status = "پرداخت نزدیک است",
        statusTone = LoanStatusTone.WARNING, linkedAccount = "حساب پرداخت: حساب جاری",
        onOpenDetails = {}, onPay = {}, onEdit = {}, onReminder = {}, onDelete = {},
        detailsDescription = "جزئیات", paymentLabel = "ثبت پرداخت", editDescription = "ویرایش",
        reminderDescription = "یادآوری", deleteDescription = "حذف"
    )
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaidLoanCardDarkPreview() = MaldarDesignTheme(true) {
    LoanCard(
        title = "وام خودرو", remainingAmount = "۰", remainingLabel = "مانده", progress = 1f,
        progressDescription = "پرداخت کامل", status = "پرداخت شد", statusTone = LoanStatusTone.POSITIVE,
        paid = true, onOpenDetails = {}, onPay = null, onEdit = {}, onReminder = {}, onDelete = {},
        detailsDescription = "جزئیات", paymentLabel = "ثبت پرداخت", editDescription = "ویرایش",
        reminderDescription = "یادآوری", deleteDescription = "حذف"
    )
}
