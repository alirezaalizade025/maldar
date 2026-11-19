package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.personalfinance.tracker.data.BankAccountEntity
import com.personalfinance.tracker.util.AppStrings

@Composable
internal fun BankAccountPicker(
    accounts: List<BankAccountEntity>, selectedId: Long?, label: String,
    allowCash: Boolean = true, excludedId: Long? = null, enabled: Boolean = true,
    onSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = accounts.firstOrNull { it.id == selectedId }?.accountLabel
                ?: if (allowCash) AppStrings.noneCash else AppStrings.selectDestinationAccount,
            onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (allowCash) DropdownMenuItem(text = { Text(AppStrings.noneCash) }, onClick = { onSelected(null); expanded = false })
            accounts.filter { it.id != excludedId }.forEach { account ->
                DropdownMenuItem(text = { Text(account.accountLabel) }, onClick = { onSelected(account.id); expanded = false })
            }
        }
    }
}
