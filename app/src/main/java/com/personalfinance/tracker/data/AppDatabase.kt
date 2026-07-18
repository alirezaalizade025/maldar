package com.personalfinance.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromTxType(value: TxType): String = value.name
    @TypeConverter
    fun toTxType(value: String): TxType = TxType.valueOf(value)

    @TypeConverter
    fun fromTxSource(value: TxSource): String = value.name
    @TypeConverter
    fun toTxSource(value: String): TxSource = TxSource.valueOf(value)

    @TypeConverter
    fun fromPendingStatus(value: PendingStatus): String = value.name
    @TypeConverter
    fun toPendingStatus(value: String): PendingStatus = PendingStatus.valueOf(value)
}

@Database(
    entities = [
        BankAccountEntity::class,
        SmsSenderEntity::class,
        TransactionEntity::class,
        PendingSmsEntity::class,
        LoanEntity::class,
        CategoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun smsSenderDao(): SmsSenderDao
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingSmsDao(): PendingSmsDao
    abstract fun loanDao(): LoanDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val defaultExpenseCategories = listOf("غذا", "حمل‌ونقل", "قبوض", "خرید", "سلامت", "تفریح", "سایر")
        private val defaultIncomeCategories = listOf("حقوق", "آزادکار", "هدیه", "سود", "سایر")

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maldar.db"
                )
                    // Personal, sideloaded app - a schema bump just resets local data rather than
                    // requiring a hand-written migration. Fine for this use case; revisit if you
                    // need to preserve data across every future update.
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = INSTANCE?.categoryDao() ?: return@launch
                                if (dao.count() == 0) {
                                    dao.insertAll(
                                        defaultExpenseCategories.map { CategoryEntity(name = it, type = TxType.EXPENSE) } +
                                        defaultIncomeCategories.map { CategoryEntity(name = it, type = TxType.INCOME) }
                                    )
                                }
                            }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
