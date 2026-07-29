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
        financialAssets: List<FinancialAssetEntity> = emptyList(),
        stockAssets: List<StockAssetEntity> = emptyList()
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
        appendLine("id,name,principal,remaining,dueDateMillis,payDay,installment,totalMonths,bankAccountId,reminderDays,notes,paid")
        loans.forEach { l ->
            appendLine(
                listOf(
                    l.id, csvCell(l.name), formatNum(l.principal), formatNum(l.remainingAmount),
                    l.dueDateMillis, l.payDayOfMonth, formatNum(l.installment), l.totalMonths,
                    l.bankAccountId ?: "", l.reminderDaysBefore, csvCell(l.notes), l.isPaid
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
        appendLine()
        appendLine("--- Stock Assets ---")
        appendLine("instrumentCode,symbol,name,quantity,buyPriceToman,lastPriceToman,lastPriceUpdatedAt")
        stockAssets.forEach { stock ->
            appendLine(
                listOf(
                    csvCell(stock.instrumentCode),
                    csvCell(stock.symbol),
                    csvCell(stock.name),
                    formatNum(stock.quantity),
                    formatNum(stock.buyPriceToman),
                    stock.lastPriceToman?.let(::formatNum) ?: "",
                    stock.lastPriceUpdatedAt ?: ""
                ).joinToString(",")
            )
        }
    }

    fun toJson(
        transactions: List<TransactionEntity>,
        accounts: List<BankAccountEntity>,
        loans: List<LoanEntity>,
        categories: List<CategoryEntity>,
        smsSenders: List<SmsSenderEntity> = emptyList(),
        financialAssets: List<FinancialAssetEntity> = emptyList(),
        stockAssets: List<StockAssetEntity> = emptyList()
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
                put(
                    "remainingMonths",
                    if (it.installment > 0.0)
                        kotlin.math.ceil(it.remainingAmount / it.installment).toInt()
                    else it.totalMonths
                )
                put("bankAccountId", it.bankAccountId)
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
        put("stockAssets", JSONArray(stockAssets.map {
            JSONObject().apply {
                put("instrumentCode", it.instrumentCode)
                put("symbol", it.symbol)
                put("name", it.name)
                put("quantity", it.quantity)
                put("buyPriceToman", it.buyPriceToman)
                put("lastPriceToman", it.lastPriceToman)
                put("lastPriceUpdatedAt", it.lastPriceUpdatedAt)
            }
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
                bankAccountId = if (o.isNull("bankAccountId")) null else o.optLong("bankAccountId"),
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
        val stockAssets = (0 until arr("stockAssets").length()).map { i ->
            val o = arr("stockAssets").getJSONObject(i)
            StockAssetEntity(
                instrumentCode = o.optString("instrumentCode", ""),
                symbol = o.optString("symbol", ""),
                name = o.optString("name", ""),
                quantity = o.optDouble("quantity", 0.0),
                buyPriceToman = o.optDouble("buyPriceToman", 0.0),
                lastPriceToman = if (o.isNull("lastPriceToman")) null else o.optDouble("lastPriceToman"),
                lastPriceUpdatedAt = if (o.isNull("lastPriceUpdatedAt")) null else o.optLong("lastPriceUpdatedAt")
            )
        }
        return FinanceRepository.ExportBundle(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            categories = categories,
            smsSenders = smsSenders,
            financialAssets = financialAssets,
            stockAssets = stockAssets
        )
    }

    /**
     * Parses a CSV backup produced by [toCsv] back into a [FinanceRepository.ExportBundle].
     * Throws [Exception] if the content is not a valid backup.
     */
    @Throws(Exception::class)
    fun fromCsv(csv: String): FinanceRepository.ExportBundle {
        require(csv.isNotBlank()) { "CSV file is empty" }
        val records = splitCsvRecords(csv.removePrefix("\uFEFF"))
        var currentSection = ""
        val rows = mutableMapOf<String, MutableList<List<String>>>()
        val headers = mutableMapOf<String, List<String>>()

        for (record in records) {
            val trimmed = record.trim()
            when {
                trimmed.startsWith("---") -> {
                    currentSection = trimmed.trim('-').trim()
                    rows[currentSection] = mutableListOf()
                }
                trimmed.isEmpty() || currentSection.isEmpty() -> {
                    // Skip empty lines or lines before any section
                }
                isHeaderLine(trimmed) -> {
                    headers[currentSection] = parseCsvLine(record)
                }
                else -> {
                    rows[currentSection]?.add(parseCsvLine(record))
                }
            }
        }
        val knownSections = setOf(
            "Transactions",
            "Bank Accounts",
            "Loans",
            "SMS Senders",
            "Categories",
            "Financial Assets",
            "Stock Assets"
        )
        require(rows.keys.any { it in knownSections }) { "No supported CSV sections were found" }

        val transactionWidth = headers["Transactions"]?.size ?: 11
        val transactions = (rows["Transactions"] ?: emptyList()).map { rawFields ->
            val fields = normalizeLegacyGroupedNumbers(rawFields, transactionWidth, setOf(2, 9))
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

        val accountWidth = headers["Bank Accounts"]?.size ?: 5
        val accounts = (rows["Bank Accounts"] ?: emptyList()).map { rawFields ->
            val fields = normalizeLegacyGroupedNumbers(rawFields, accountWidth, setOf(accountWidth - 1))
            if (fields.size < 4) throw Exception("Invalid account row: expected at least 4 fields, got ${fields.size}")
            val hasLast4 = headers["Bank Accounts"]?.contains("accountLast4") == true || fields.size >= 5
            BankAccountEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                bankName = fields[1].removeSurrounding("\""),
                accountLabel = fields[2].removeSurrounding("\""),
                accountLast4 = if (hasLast4) fields[3].removeSurrounding("\"") else "",
                balance = fields[if (hasLast4) 4 else 3].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0
            )
        }

        val loanWidth = headers["Loans"]?.size ?: 12
        val loans = (rows["Loans"] ?: emptyList()).map { rawFields ->
            val fields = normalizeLegacyGroupedNumbers(rawFields, loanWidth, setOf(2, 3, 6))
            if (fields.size < 9) throw Exception("Invalid loan row: expected at least 9 fields, got ${fields.size}")
            val hasNewLoanFields = fields.size >= 11
            val hasAccountField = headers["Loans"]?.contains("bankAccountId") == true || fields.size >= 12
            LoanEntity(
                id = fields[0].toLongOrNull() ?: 0L,
                name = fields[1].removeSurrounding("\""),
                principal = fields[2].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                remainingAmount = fields[3].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                dueDateMillis = fields[4].toLongOrNull() ?: 0L,
                payDayOfMonth = fields[5].toIntOrNull() ?: 1,
                installment = if (hasNewLoanFields) fields[6].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0 else 0.0,
                totalMonths = if (hasNewLoanFields) fields[7].toIntOrNull() ?: 0 else 0,
                bankAccountId = if (hasAccountField) fields[8].takeIf { it.isNotEmpty() }?.toLongOrNull() else null,
                reminderDaysBefore = fields[when {
                    hasAccountField -> 9
                    hasNewLoanFields -> 8
                    else -> 6
                }].toIntOrNull() ?: 3,
                notes = fields[when {
                    hasAccountField -> 10
                    hasNewLoanFields -> 9
                    else -> 7
                }],
                isPaid = fields[when {
                    hasAccountField -> 11
                    hasNewLoanFields -> 10
                    else -> 8
                }].toBoolean()
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
        val assetWidth = headers["Financial Assets"]?.size ?: 2
        val financialAssets = (rows["Financial Assets"] ?: emptyList()).map { rawFields ->
            val fields = normalizeLegacyGroupedNumbers(rawFields, assetWidth, setOf(1))
            require(fields.size >= 2) { "Invalid financial asset row" }
            FinancialAssetEntity(
                type = enumValueOf<AssetType>(fields[0]),
                quantityGrams = fields[1].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0
            )
        }
        val stockWidth = headers["Stock Assets"]?.size ?: 7
        val stockAssets = (rows["Stock Assets"] ?: emptyList()).map { rawFields ->
            val fields = normalizeLegacyGroupedNumbers(rawFields, stockWidth, setOf(3, 4, 5))
            require(fields.size >= 5) { "Invalid stock asset row" }
            StockAssetEntity(
                instrumentCode = fields[0].removeSurrounding("\""),
                symbol = fields[1].removeSurrounding("\""),
                name = fields[2].removeSurrounding("\""),
                quantity = fields[3].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                buyPriceToman = fields[4].removeSurrounding("\"").replace(",", "").toDoubleOrNull() ?: 0.0,
                lastPriceToman = fields.getOrNull(5)?.takeIf { it.isNotEmpty() }
                    ?.removeSurrounding("\"")?.replace(",", "")?.toDoubleOrNull(),
                lastPriceUpdatedAt = fields.getOrNull(6)?.takeIf { it.isNotEmpty() }?.toLongOrNull()
            )
        }

        return FinanceRepository.ExportBundle(
            transactions = transactions,
            accounts = accounts,
            loans = loans,
            categories = categories,
            smsSenders = smsSenders,
            financialAssets = financialAssets,
            stockAssets = stockAssets
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

    /** Split records without breaking quoted fields that contain line breaks. */
    private fun splitCsvRecords(csv: String): List<String> {
        val records = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            if (c == '"') {
                current.append(c)
                if (quoted && i + 1 < csv.length && csv[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    quoted = !quoted
                }
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (current.isNotEmpty()) {
                    records += current.toString()
                    current.clear()
                }
                if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
            } else {
                current.append(c)
            }
            i++
        }
        require(!quoted) { "CSV contains an unclosed quoted field" }
        if (current.isNotEmpty()) records += current.toString()
        return records
    }

    /**
     * Repairs backups made by older versions that placed thousands separators in
     * numeric CSV fields without quoting them.
     */
    private fun normalizeLegacyGroupedNumbers(
        fields: List<String>,
        expectedCount: Int,
        expandableColumns: Set<Int>
    ): List<String> {
        if (fields.size <= expectedCount) return fields

        fun solve(column: Int, token: Int, out: MutableList<String>): List<String>? {
            if (column == expectedCount) return if (token == fields.size) out.toList() else null
            val columnsLeft = expectedCount - column - 1
            val maxTake = fields.size - token - columnsLeft
            val takeRange = if (column in expandableColumns) maxTake downTo 1 else 1..1
            for (take in takeRange) {
                if (token + take > fields.size) continue
                val value = fields.subList(token, token + take).joinToString(",")
                if (take > 1 && value.replace(",", "").toDoubleOrNull() == null) continue
                out += value
                val result = solve(column + 1, token + take, out)
                if (result != null) return result
                out.removeAt(out.lastIndex)
            }
            return null
        }

        return solve(0, 0, mutableListOf())
            ?: throw IllegalArgumentException("CSV row has ${fields.size} fields; expected $expectedCount")
    }

    private fun isHeaderLine(line: String): Boolean {
        // Check if line starts with common header keywords
        return line.startsWith("id,") || 
               line.contains("id,type,") ||
               line.startsWith("type,") ||
               line.startsWith("bankName,") ||
               line.startsWith("name,") ||
               line.startsWith("senderId,") ||
               line.startsWith("instrumentCode,")
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
