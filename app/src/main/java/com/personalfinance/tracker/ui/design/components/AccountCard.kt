package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

@Composable
fun AccountCard(
    title: String,
    bankName: String,
    balance: String,
    modifier: Modifier = Modifier,
    currency: String = "تومان",
    maskedIdentifier: String? = null,
    status: String? = null,
    loanSummary: List<String> = emptyList(),
    monthlyInstallmentRemainder: String? = null,
    refreshing: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onSms: (() -> Unit)?,
    onDelete: () -> Unit,
    editDescription: String,
    refreshDescription: String,
    smsDescription: String,
    deleteDescription: String
) {
    AppCard(modifier = modifier, style = AppCardStyle.RAISED) {
        Column(Modifier.fillMaxWidth()) {
            val summary = buildString {
                append(title)
                append("، ")
                append(bankName)
                maskedIdentifier?.let { append("، $it") }
                append("، $balance $currency")
                status?.let { append("، $it") }
            }
            val headerModifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = summary }
                .let { base ->
                    if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base
                }
            Column(headerModifier) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            bankName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        maskedIdentifier?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.ContentOrLtr),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AmountText(balance, currency = currency, style = MaterialTheme.typography.titleMedium)
                        if (status != null) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = if (refreshing) MaldarDesign.colors.warningContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (refreshing) MaldarDesign.colors.onWarning else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Text(
                                    status,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).semantics { contentDescription = status },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                if (loanSummary.isNotEmpty() || monthlyInstallmentRemainder != null) {
                    Spacer(Modifier.height(12.dp))
                    loanSummary.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    monthlyInstallmentRemainder?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaldarDesign.colors.negative)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier.semantics {
                        contentDescription = refreshDescription
                        if (refreshing) stateDescription = status ?: refreshDescription
                    }
                ) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = refreshDescription)
                }
                IconButton(onClick = { onSms?.invoke() }, enabled = onSms != null) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = smsDescription)
                }
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
private fun AccountCardPreview() = MaldarDesignTheme {
    AccountCard(
        title = "حساب روزمره",
        bankName = "بانک ملت",
        balance = "۶٬۳۰۰٬۰۰۰",
        maskedIdentifier = "•••• ۵۶۷۲",
        status = "فعال",
        loanSummary = listOf("مانده وام: ۲٬۰۰۰٬۰۰۰ تومان"),
        onEdit = {}, onRefresh = {}, onSms = {}, onDelete = {},
        editDescription = "ویرایش", refreshDescription = "به‌روزرسانی",
        smsDescription = "پیامک‌ها", deleteDescription = "حذف"
    )
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountCardDarkPreview() = MaldarDesignTheme(true) {
    AccountCard(
        title = "حساب پس‌انداز", bankName = "بانک سامان", balance = "۴٬۲۰۰٬۰۰۰",
        refreshing = true, status = "در حال به‌روزرسانی",
        onEdit = {}, onRefresh = {}, onSms = null, onDelete = {},
        editDescription = "ویرایش", refreshDescription = "به‌روزرسانی",
        smsDescription = "پیامک‌ها", deleteDescription = "حذف"
    )
}
