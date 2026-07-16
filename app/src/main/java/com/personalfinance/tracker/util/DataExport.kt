package com.personalfinance.tracker.util

import com.personalfinance.tracker.data.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds human-readable CSV and JSON exports of all local data so the user can
 * back up or move their records. Everything lives in the Room DB, so this is the
 * only safe way to preserve data before a destructive schema migration.
 */
object DataExport {

    fun toCsv(
        transactions: List<TransactionEntity>,
        accounts: List<BankAccountEntity>,
        loans: List<LoanEntity>,
        categories: List<CategoryEntity>
    ): String = buildString {
        appendLine("--- Transactions ---")
        appendLine("id,type,amount,category,note,dateMillis,bankAccountId,loanId,source")
        transactions.forEach { t ->
            appendLine(
                listOf(
                    t.id, t.type, formatNum(t.amount), csvCell(t.category),
                    csvCell(t.note), t.dateMillis, t.bankAccountId ?: "", t.loanId ?: "", t.source
                ).joinToString(",")
            )
        }
        appendLine()
        appendLine("--- Bank Accounts ---")
        appendLine("id,bankName,label,last4,balance")
        accounts.forEach { a ->
            appendLine(listOf(a.id, csvCell(a.bankName), csvCell(a.accountLabel), csvCell(a.accountLast4), formatNum(a.balance)).joinToString(","))
        }
        appendLine()
        appendLine("--- Loans ---")
        appendLine("id,name,principal,remaining,dueDateMillis,payDay,reminderDays,notes,paid")
        loans.forEach { l ->
            appendLine(
                listOf(
                    l.id, csvCell(l.name), formatNum(l.principal), formatNum(l.remainingAmount),
                    l.dueDateMillis, l.payDayOfMonth, l.reminderDaysBefore, csvCell(l.notes), l.isPaid
                ).joinToString(",")
            )
        }
        appendLine()
        appendLine("--- Categories ---")
        appendLine("id,name,type")
        categories.forEach { c -> appendLine(listOf(c.id, csvCell(c.name), c.type).joinToString(",")) }
    }

    fun toJson(
        transactions: List<TransactionEntity>,
        accounts: List<BankAccountEntity>,
        loans: List<LoanEntity>,
        categories: List<CategoryEntity>
    ): String = JSONObject().apply {
        put("transactions", JSONArray(transactions.map {
            JSONObject().apply {
                put("id", it.id); put("type", it.type.name); put("amount", it.amount)
                put("category", it.category); put("note", it.note)
                put("dateMillis", it.dateMillis); put("bankAccountId", it.bankAccountId); put("loanId", it.loanId)
                put("source", it.source.name); put("rawSms", it.rawSms)
            }
        }))
        put("bankAccounts", JSONArray(accounts.map {
            JSONObject().apply {
                put("id", it.id); put("bankName", it.bankName); put("accountLabel", it.accountLabel)
                put("accountLast4", it.accountLast4); put("balance", it.balance)
            }
        }))
        put("loans", JSONArray(loans.map {
            JSONObject().apply {
                put("id", it.id); put("name", it.name); put("principal", it.principal)
                put("remainingAmount", it.remainingAmount); put("dueDateMillis", it.dueDateMillis)
                put("payDayOfMonth", it.payDayOfMonth); put("reminderDaysBefore", it.reminderDaysBefore)
                put("notes", it.notes); put("isPaid", it.isPaid)
            }
        }))
        put("categories", JSONArray(categories.map {
            JSONObject().apply { put("id", it.id); put("name", it.name); put("type", it.type.name) }
        }))
    }.toString(2)

    private fun csvCell(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    private fun formatNum(v: Double): String = "%,.2f".format(java.util.Locale.US, v)
}
