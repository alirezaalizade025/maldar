package com.personalfinance.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxType { INCOME, EXPENSE }
enum class TxSource { MANUAL, SMS }
enum class PendingStatus { PENDING, CONFIRMED, REJECTED }

@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val accountLabel: String,        // e.g. "Savings - HDFC"
    val accountLast4: String,        // last 4 digits, for matching SMS text
    val balance: Double = 0.0
)

@Entity(tableName = "sms_senders")
data class SmsSenderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderId: String,             // e.g. "HDFCBK", "VM-SBIINB", or a phone number
    val bankAccountId: Long,          // which bank account this sender maps to
    val label: String = ""
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TxType,
    val category: String,
    val note: String = "",
    val dateMillis: Long,
    val bankAccountId: Long? = null,
    val source: TxSource = TxSource.MANUAL,
    val rawSms: String? = null
)

@Entity(tableName = "pending_sms")
data class PendingSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawMessage: String,
    val sender: String,
    val parsedAmount: Double?,
    val parsedType: TxType?,
    val timestampMillis: Long,
    val bankAccountId: Long?,
    val status: PendingStatus = PendingStatus.PENDING
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val principal: Double,
    val remainingAmount: Double,
    val dueDateMillis: Long,
    val reminderDaysBefore: Int = 3,
    val notes: String = "",
    val isPaid: Boolean = false
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxType   // which picker (expense/income) this category shows up in
)
