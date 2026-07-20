package com.personalfinance.tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.personalfinance.tracker.data.CategoryEntity
import com.personalfinance.tracker.data.TxType
import com.personalfinance.tracker.util.AppStrings
import com.personalfinance.tracker.util.fa
import com.personalfinance.tracker.viewmodel.FinanceViewModel

/**
 * Dropdown for picking a category, with a "Manage categories" option at the bottom
 * that opens add/rename/delete controls. Reused in Add Transaction and SMS Confirm screens
 * so category edits stay in sync everywhere.
 */
@Composable
fun CategoryPicker(
    viewModel: FinanceViewModel,
    type: TxType,
    selected: String,
    onSelected: (String) -> Unit
) {
    val categories by if (type == TxType.EXPENSE) viewModel.expenseCategories.collectAsState()
                        else viewModel.incomeCategories.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // If nothing is selected yet (or the selection no longer exists), default to the first category.
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && categories.none { it.name == selected }) {
            onSelected(categories.first().name)
        }
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true,
            label = { Text("Category") },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelected(c.name); expanded = false })
            }
            Divider()
            DropdownMenuItem(
                text = { Text(AppStrings.manageCategories) },
                onClick = { expanded = false; showManage = true }
            )
        }
    }

    if (showManage) {
        ManageCategoriesDialog(
            viewModel = viewModel,
            type = type,
            categories = categories,
            scope = scope,
            onDismiss = { showManage = false }
        )
    }
}

@Composable
private fun ManageCategoriesDialog(
    viewModel: FinanceViewModel,
    type: TxType,
    categories: List<CategoryEntity>,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<CategoryEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == TxType.EXPENSE) AppStrings.manageExpenseCategories else AppStrings.manageIncomeCategories) },
        text = {
            Column {
                LazyColumnLike(categories) { cat ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (renaming?.id == cat.id) {
                            OutlinedTextField(
                                value = renameText, onValueChange = { renameText = it },
                                modifier = Modifier.weight(1f), singleLine = true
                            )
                            TextButton(onClick = {
                                if (renameText.isNotBlank()) viewModel.renameCategory(cat, renameText)
                                renaming = null
                            }) { Text("Save") }
                        } else {
                            Text(cat.name, modifier = Modifier.weight(1f).padding(top = 12.dp))
                            IconButton(onClick = { renaming = cat; renameText = cat.name }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val res = viewModel.deleteCategorySafe(cat)
                                    resultMessage = if (res.reassignedCount > 0)
                                        AppStrings.categoryDeletedReassigned.fa(res.reassignedCount)
                                    else
                                        AppStrings.categoryDeleted
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("New category") },
                        modifier = Modifier.weight(1f), singleLine = true
                    )
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.addCategory(newName, type)
                            newName = ""
                        }
                    }) { Text("Add") }
                }
                resultMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

// Small helper so the dialog list doesn't need a lazy list dependency inside AlertDialog content.
@Composable
private fun LazyColumnLike(items: List<CategoryEntity>, itemContent: @Composable (CategoryEntity) -> Unit) {
    Column { items.forEach { itemContent(it) } }
}
