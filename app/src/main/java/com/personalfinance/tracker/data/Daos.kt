package com.personalfinance.tracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY id DESC")
    fun getAll(): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    suspend fun getById(id: Long): BankAccountEntity?

    @Insert
    suspend fun insert(account: BankAccountEntity): Long

    @Update
    suspend fun update(account: BankAccountEntity)

    @Delete
    suspend fun delete(account: BankAccountEntity)

    @Query("SELECT * FROM bank_accounts ORDER BY id DESC")
    suspend fun getAllOnce(): List<BankAccountEntity>

    @Query("UPDATE bank_accounts SET balance = balance + :delta WHERE id = :id")
    suspend fun adjustBalance(id: Long, delta: Double)
}

@Dao
interface SmsSenderDao {
    @Query("SELECT * FROM sms_senders ORDER BY id DESC")
    fun getAll(): Flow<List<SmsSenderEntity>>

    @Query("SELECT * FROM sms_senders")
    suspend fun getAllOnce(): List<SmsSenderEntity>

    @Insert
    suspend fun insert(sender: SmsSenderEntity): Long

    @Delete
    suspend fun delete(sender: SmsSenderEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dateMillis BETWEEN :start AND :end ORDER BY dateMillis DESC")
    fun getBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Delete
    suspend fun delete(tx: TransactionEntity)

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type AND dateMillis BETWEEN :start AND :end")
    suspend fun sumByTypeBetween(type: TxType, start: Long, end: Long): Double?

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND dateMillis BETWEEN :start AND :end GROUP BY category ORDER BY total DESC")
    suspend fun expenseByCategoryBetween(start: Long, end: Long): List<CategoryTotal>

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE loanId IS NOT NULL AND dateMillis BETWEEN :start AND :end ORDER BY dateMillis DESC")
    suspend fun getPaymentsBetween(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE loanId = :loanId ORDER BY dateMillis DESC")
    suspend fun getPaymentsForLoan(loanId: Long): List<TransactionEntity>
}

@Dao

data class CategoryTotal(val category: String, val total: Double)

@Dao
interface PendingSmsDao {
    @Query("SELECT * FROM pending_sms WHERE status = 'PENDING' ORDER BY timestampMillis DESC")
    fun getPending(): Flow<List<PendingSmsEntity>>

    @Query("SELECT * FROM pending_sms WHERE status != 'PENDING' ORDER BY timestampMillis DESC")
    fun getReviewed(): Flow<List<PendingSmsEntity>>

    @Query("SELECT * FROM pending_sms ORDER BY timestampMillis DESC")
    fun getAll(): Flow<List<PendingSmsEntity>>

    @Insert
    suspend fun insert(pending: PendingSmsEntity): Long

    @Update
    suspend fun update(pending: PendingSmsEntity)

    @Delete
    suspend fun delete(pending: PendingSmsEntity)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY dueDateMillis ASC")
    fun getAll(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE isPaid = 0")
    suspend fun getActiveLoans(): List<LoanEntity>

    @Insert
    suspend fun insert(loan: LoanEntity): Long

    @Update
    suspend fun update(loan: LoanEntity)

    @Delete
    suspend fun delete(loan: LoanEntity)

    @Query("SELECT * FROM loans ORDER BY dueDateMillis ASC")
    suspend fun getAllOnce(): List<LoanEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY name ASC")
    fun getByType(type: TxType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllOnce(): List<CategoryEntity>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE category = :name")
    suspend fun countTransactionsWithCategory(name: String): Int

    @Query("UPDATE transactions SET category = :target WHERE category = :name")
    suspend fun reassignTransactionsCategory(name: String, target: String)

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)
}
