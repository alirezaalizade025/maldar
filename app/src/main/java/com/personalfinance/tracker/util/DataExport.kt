package com.personalfinance.tracker.util

import com.personalfinance.tracker.data.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds and parses full-data backups so the user can move or restore their
 * records. Everything lives in the Room DB, so this is the only safe way to
 * preserve data before a destructive schema migration.
 *
 * JSON is the canonical round-trippable format (see [toJson]/[fromJson]).
 * CSV is kept as a human-readable companion export.
 */
object DataExport {

    fun toCsv(
        transactions: List<TransactionEntity>,
        accounts: List<BankAccountEntity>,
        loans: List<LoanEntity>,
        categories: List<CategoryEntity>,
        smsSenders: List<SmsSenderEntity> = emptyList()
    ): String = buildString {
        appendLine("--- Transactions ---")
        appendLine("id,type,amount,category,note,dateMillis,bankAccountId,loanId,source,balanceAfter")
        transactions.forEach { t ->
            appendLine(
                listOf(
                    t.id, t.type, formatNum(t.amount), csvCell(t.category),
                    csvCell(t.note), t.dateMillis, t.bankAccountId ?: "", t.loanId ?: "", t.source,
                    t.balanceAfter?.let { formatNum(it) } ?: ""
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
        appendLine("--- SMS Senders ---")
        appendLine("id,senderId,bankAccountId,label")
        smsSenders.forEach { s ->
            appendLine(listOf(s.id, csvCell(s.senderId), s.bankAccountId, csvCell(s.label)).joinToString(","))
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
        categories: List<CategoryEntity>,
        smsSenders: List<SmsSenderEntity> = emptyList()
    ): String = JSONObject().apply {
        put("transactions", JSONArray(transactions.map {
            JSONObject().apply {
                put("id", it.id); put("type", it.type.name); put("amount", it.amount)
                put("category", it.category); put("note", it.note)
                put("dateMillis", it.dateMillis); put("bankAccountId", it.bankAccountId); put("loanId", it.loanId)
                put("source", it.source.name); put("rawSms", it.rawSms); put("balanceAfter", it.balanceAfter)
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
        put("smsSenders", JSONArray(smsSenders.map {
            JSONObject().apply {
                put("id", it.id); put("senderId", it.senderId); put("bankAccountId", it.bankAccountId)
                put("label", it.label)
            }
        }))
        put("categories", JSONArray(categories.map {
            JSONObject().apply { put("id", it.id); put("name", it.name); put("type", it.type.name) }
        }))
    }.toString(2)

    /**
     * Parses a JSON backup produced by [toJson] back into an [FinanceRepository.ExportBundle].
     * Throws [Exception] if the content is not a valid backup. IDs are preserved so
     * relationships survive the round-trip.
     */
    @Throws(Exception::class)
    fun fromJson(json: String): FinanceRepository.ExportBundle {
        val root = JSONObject(json)
        fun arr(name: String) = root.optJSONArray(name) ?: JSONArray()

        val transactions = (0 until arr("transactions").length()).map { i ->
            val o = arr("transactions").getJSONObject(i)
            TransactionEntity(
                id = o.optLong("id"),
                amount = o.optDouble("amount"),
                type = enumValueOf<TxType>(o.optString("type", "EXPENSE")),
                category = o.optString("category", ""),
                note = o.optString("note", ""),
                dateMillis = o.optLong("dateMillis"),
                bankAccountId = if (o.isNull("bankAccountId")) null else o.optLong("bankAccountId"),
                source = enumValueOf<TxSource>(o.optString("source", "MANUAL")),
                rawSms = if (o.isNull("rawSms")) null else o.optString("rawSms", null),
                balanceAfter = if (o.isNull("balanceAfter")) null else o.optDouble("balanceAfter"),
                loanId = if (o.isNull("loanId")) null else o.optLong("loanId")
            )
        }
        val accounts = (0 until arr("bankAccounts").length()).map { i ->
            val o = arr("bankAccounts").getJSONObject(i)
            BankAccountEntity(
                id = o.optLong("id"),
                bankName = o.optString("bankName", ""),
                accountLabel = o.optString("accountLabel", ""),
                accountLast4 = o.optString("accountLast4", ""),
                balance = o.optDouble("balance", 0.0)
            )
        }
        val loans = (0 until arr("loans").length()).map { i ->
            val o = arr("loans").getJSONObject(i)
            LoanEntity(
                id = o.optLong("id"),
                name = o.optString("name", ""),
                principal = o.optDouble("principal", 0.0),
                remainingAmount = o.optDouble("remainingAmount", 0.0),
                dueDateMillis = o.optLong("dueDateMillis"),
                payDayOfMonth = o.optInt("payDayOfMonth", 1),
                installment = o.optDouble("installment", 0.0),
                totalMonths = o.optInt("totalMonths", 0),
                reminderDaysBefore = o.optInt("reminderDaysBefore", 3),
                notes = o.optString("notes", ""),
                isPaid = o.optBoolean("isPaid", false)
            )
        }
        val smsSenders = (0 until arr("smsSenders").length()).map { i ->
            val o = arr("smsSenders").getJSONObject(i)
            SmsSenderEntity(
                id = o.optLong("id"),
                senderId = o.optString("senderId", ""),
                bankAccountId = if (o.isNull("bankAccountId")) 0L else o.optLong("bankAccountId"),
                label = o.optString("label", "")
            )
        }
        val categories = (0 until arr("categories").length()).map { i ->
            val o = arr("categories").getJSONObject(i)
            CategoryEntity(
                id = o.optLong("id"),
                name = o.optString("name", ""),
                type = enumValueOf<TxType>(o.optString("type", "EXPENSE"))
            )
        }
        return FinanceRepository.ExportBundle(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            categories = categories,
            smsSenders = smsSenders
        )
    }

    private fun csvCell(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    private fun formatNum(v: Double): String = "%,.2f".format(java.util.Locale.US, v)
}
