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
        smsSenders: List<SmsSenderEntity> = emptyList(),
        financialAssets: List<FinancialAssetEntity> = emptyList()
    ): String = buildString {
        appendLine("--- Transactions ---")
        appendLine("id,type,amount,category,note,dateMillis,bankAccountId,loanId,source,balanceAfter,rawSms")
        transactions.forEach { t ->
            appendLine(
                listOf(
                    t.id, t.type, formatNum(t.amount), csvCell(t.category),
                    csvCell(t.note), t.dateMillis, t.bankAccountId ?: "", t.loanId ?: "", t.source,
                    t.balanceAfter?.let { formatNum(it) } ?: "", csvCell(t.rawSms ?: "")
                ).joinToString(",")
            )
        }
        appendLine()
        appendLine("--- Bank Accounts ---")
        appendLine("id,bankName,label,accountLast4,balance")
        accounts.forEach { a ->
            appendLine(listOf(a.id, csvCell(a.bankName), csvCell(a.accountLabel), csvCell(a.accountLast4), formatNum(a.balance)).joinToString(","))
        }
        appendLine()
        appendLine("--- Loans ---")
        appendLine("id,name,principal,remaining,dueDateMillis,payDay,installment,totalMonths,reminderDays,notes,paid")
        loans.forEach { l ->
            appendLine(
                listOf(
                    l.id, csvCell(l.name), formatNum(l.principal), formatNum(l.remainingAmount),
                    l.dueDateMillis, l.payDayOfMonth, formatNum(l.installment), l.totalMonths,
                    l.reminderDaysBefore, csvCell(l.notes), l.isPaid
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
        appendLine()
        appendLine("--- Financial Assets ---")
        appendLine("type,quantityGrams")
        financialAssets.forEach { a -> appendLine("${a.type},${formatNum(a.quantityGrams)}") }
    }

    fun toJson(
        transactions: List<TransactionEntity>,
        accounts: List<BankAccountEntity>,
        loans: List<LoanEntity>,
        categories: List<CategoryEntity>,
        smsSenders: List<SmsSenderEntity> = emptyList(),
        financialAssets: List<FinancialAssetEntity> = emptyList()
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
                put("installment", it.installment); put("totalMonths", it.totalMonths)
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
        put("financialAssets", JSONArray(financialAssets.map {
            JSONObject().apply { put("type", it.type.name); put("quantityGrams", it.quantityGrams) }
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
        val financialAssets = (0 until arr("financialAssets").length()).map { i ->
            val o = arr("financialAssets").getJSONObject(i)
            FinancialAssetEntity(
                type = enumValueOf<AssetType>(o.optString("type", "GOLD_18K")),
                quantityGrams = o.optDouble("quantityGrams", 0.0)
            )
        }
        return FinanceRepository.ExportBundle(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            categories = categories,
            smsSenders = smsSenders,
            financialAssets = financialAssets
        )
    }

    /**
     * Parses a CSV backup produced by [toCsv] back into a [FinanceRepository.ExportBundle].
     * Throws [Exception] if the content is not a valid backup.
     */
    @Throws(Exception::class)
    fun fromCsv(csv: String): FinanceRepository.ExportBundle {
        val lines = csv.lines()
        var currentSection = ""
        val rows = mutableMapOf<String, MutableList<List<String>>>()
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("---") -> {
                    currentSection = trimmed.trim('-').trim()
                    rows[currentSection] = mutableListOf()
                }
                trimmed.isEmpty() || currentSection.isEmpty() -> {
                    // Skip empty lines or lines before any section
                }
                isHeaderLine(trimmed) -> {
                    // Skip header lines (id, type, amount, ...)
                }
                else -> {
                    rows[currentSection]?.add(parseCsvLine(trimmed))
                }
            }
        }

        val transactions = (rows["Transactions"] ?: emptyList()).map { fields ->
            if (fields.size < 7) throw Exception("Invalid transaction row: expected at least 7 fields, got ${fields.size}")
            TransactionEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                type = try { enumValueOf<TxType>(fields[1]) } catch (e: Exception) { TxType.EXPENSE },
                amount = fields[2].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                category = fields[3].removeSurrounding("\""),
                note = fields[4].removeSurrounding("\""),
                dateMillis = fields[5].toLongOrNull() ?: 0L,
                bankAccountId = if (fields[6].isEmpty()) null else fields[6].toLongOrNull(),
                loanId = fields.getOrNull(7)?.takeIf { it.isNotEmpty() }?.toLongOrNull(),
                source = try { enumValueOf<TxSource>(fields.getOrNull(8) ?: "MANUAL") } catch (e: Exception) { TxSource.MANUAL },
                balanceAfter = fields.getOrNull(9)?.takeIf { it.isNotEmpty() }
                    ?.removeSurrounding("\"")?.replace(",", "")?.toDoubleOrNull(),
                rawSms = fields.getOrNull(10)?.takeIf { it.isNotEmpty() }?.removeSurrounding("\"")
            )
        }

        val accounts = (rows["Bank Accounts"] ?: emptyList()).map { fields ->
            if (fields.size < 4) throw Exception("Invalid account row: expected at least 4 fields, got ${fields.size}")
            val hasLast4 = fields.size >= 5
            BankAccountEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                bankName = fields[1].removeSurrounding("\""),
                accountLabel = fields[2].removeSurrounding("\""),
                accountLast4 = if (hasLast4) fields[3].removeSurrounding("\"") else "",
                balance = fields[if (hasLast4) 4 else 3].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0
            )
        }

        val loans = (rows["Loans"] ?: emptyList()).map { fields ->
            if (fields.size < 9) throw Exception("Invalid loan row: expected at least 9 fields, got ${fields.size}")
            val hasNewLoanFields = fields.size >= 11
            LoanEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                name = fields[1].removeSurrounding("\""),
                principal = fields[2].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                remainingAmount = fields[3].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                dueDateMillis = fields[4].toLongOrNull() ?: 0L,
                payDayOfMonth = fields[5].toIntOrNull() ?: 1,
                installment = if (hasNewLoanFields) fields[6].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0 else 0.0,
                totalMonths = if (hasNewLoanFields) fields[7].toIntOrNull() ?: 0 else 0,
                reminderDaysBefore = fields[if (hasNewLoanFields) 8 else 6].toIntOrNull() ?: 3,
                notes = fields[if (hasNewLoanFields) 9 else 7].removeSurrounding("\""),
                isPaid = fields[if (hasNewLoanFields) 10 else 8].toBoolean()
            )
        }

        val smsSenders = (rows["SMS Senders"] ?: emptyList()).map { fields ->
            require(fields.size >= 4) { "Invalid SMS sender row" }
            SmsSenderEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                senderId = fields[1].removeSurrounding("\""),
                bankAccountId = fields[2].toLongOrNull() ?: 0L,
                label = fields[3].removeSurrounding("\"")
            )
        }

        val categories = (rows["Categories"] ?: emptyList()).map { fields ->
            require(fields.size >= 3) { "Invalid category row" }
            CategoryEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                name = fields[1].removeSurrounding("\""),
                type = try { enumValueOf<TxType>(fields[2]) } catch (e: Exception) { TxType.EXPENSE }
            )
        }
        val financialAssets = (rows["Financial Assets"] ?: emptyList()).map { fields ->
            require(fields.size >= 2) { "Invalid financial asset row" }
            FinancialAssetEntity(
                type = enumValueOf<AssetType>(fields[0]),
                quantityGrams = fields[1].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0
            )
        }

        return FinanceRepository.ExportBundle(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            categories = categories,
            smsSenders = smsSenders,
            financialAssets = financialAssets
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var current = StringBuilder()
        var insideQuotes = false
        var i = 0
        
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    fields.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun isHeaderLine(line: String): Boolean {
        // Check if line starts with common header keywords
        return line.startsWith("id,") || 
               line.contains("id,type,") ||
               line.startsWith("type,") ||
               line.startsWith("bankName,") ||
               line.startsWith("name,") ||
               line.startsWith("senderId,")
    }

    private fun csvCell(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
    }

    // Machine-readable CSV numbers: no grouping commas and no exponent notation.
    private fun formatNum(v: Double): String =
        java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
}
