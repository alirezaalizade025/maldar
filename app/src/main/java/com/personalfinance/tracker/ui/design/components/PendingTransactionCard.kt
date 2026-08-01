package com.personalfinance.tracker.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.personalfinance.tracker.ui.design.MaldarDesign
import com.personalfinance.tracker.ui.design.MaldarDesignTheme

@Composable
fun PendingTransactionCard(
    typeLabel: String,
    amount: String,
    sender: String,
    rawMessage: String,
    confirmLabel: String,
    editLabel: String,
    rejectLabel: String,
    rawMessageLabel: String,
    expandDescription: String,
    collapseDescription: String,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    account: String? = null,
    category: String? = null,
    date: String? = null,
    balanceAfter: String? = null,
    warningText: String? = null,
    loading: Boolean = false
) {
    var rawExpanded by remember { mutableStateOf(false) }
    val summary = listOfNotNull(typeLabel, amount, sender, account, category, date, balanceAfter).joinToString("، ")

    AppCard(modifier = modifier.semantics { contentDescription = summary }, style = AppCardStyle.RAISED) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaldarDesign.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(sender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(typeLabel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            account?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            category?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            balanceAfter?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            warningText?.let { WarningBanner(it) }

            TextButton(
                onClick = { rawExpanded = !rawExpanded },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).semantics {
                    contentDescription = if (rawExpanded) collapseDescription else expandDescription
                    stateDescription = if (rawExpanded) collapseDescription else expandDescription
                },
                enabled = !loading
            ) {
                Text(rawMessageLabel)
                Icon(if (rawExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (rawExpanded) {
                Text(
                    rawMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(2.dp))
            AppButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), enabled = !loading, loading = loading)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AppButton(editLabel, onEdit, style = AppButtonStyle.OUTLINED, enabled = !loading)
                TextButton(
                    onClick = onReject,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    enabled = !loading,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(rejectLabel) }
            }
        }
    }
}

@Preview(showBackground = true, locale = "fa")
@Composable
private fun PendingTransactionCardPreview() = MaldarDesignTheme {
    PendingTransactionCard(
        typeLabel = "هزینه", amount = "۱٬۲۵۰٬۰۰۰ تومان", sender = "بانک ملت",
        account = "حساب پیشنهادی: حساب روزمره", category = "دسته‌بندی: سایر",
        date = "تاریخ: ۱۲ مرداد ۱۴۰۵", balanceAfter = "مانده: ۸٬۴۰۰٬۰۰۰ تومان",
        rawMessage = "برداشت از کارت شما انجام شد.", confirmLabel = "تایید",
        editLabel = "ویرایش و تایید", rejectLabel = "نادیده گرفتن", rawMessageLabel = "متن پیامک",
        expandDescription = "نمایش متن پیامک", collapseDescription = "بستن متن پیامک",
        onConfirm = {}, onEdit = {}, onReject = {}
    )
}

@Preview(showBackground = true, locale = "fa", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingTransactionCardDarkPreview() = MaldarDesignTheme(true) {
    PendingTransactionCard(
        typeLabel = "نامشخص", amount = "مبلغ نامشخص", sender = "شماره ناشناس",
        warningText = "مبلغ پیامک تشخیص داده نشد.", rawMessage = "متن طولانی پیامک بانکی",
        confirmLabel = "تایید", editLabel = "ویرایش و تایید", rejectLabel = "نادیده گرفتن",
        rawMessageLabel = "متن پیامک", expandDescription = "نمایش متن پیامک",
        collapseDescription = "بستن متن پیامک", onConfirm = {}, onEdit = {}, onReject = {}
    )
}
