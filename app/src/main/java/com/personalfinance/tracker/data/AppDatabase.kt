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
import java.io.File

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

        private const val DB_NAME = "maldar.db"

        private val defaultExpenseCategories = listOf("غذا", "حمل‌ونقل", "قبوض", "خرید", "سلامت", "تفریح", "سایر")
        private val defaultIncomeCategories = listOf("حقوق", "آزادکار", "هدیه", "سود", "سایر")

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                // Personal, sideloaded app - a schema bump just resets local data rather than
                // requiring a hand-written migration. Fine for this use case; revisit if you
                // need to preserve data across every future update.
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        val built = this@Companion.INSTANCE
                        if (built != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = built.categoryDao()
                                if (dao.count() == 0) {
                                    dao.insertAll(
                                        defaultExpenseCategories.map { CategoryEntity(name = it, type = TxType.EXPENSE) } +
                                        defaultIncomeCategories.map { CategoryEntity(name = it, type = TxType.INCOME) }
                                    )
                                }
                            }
                        }
                    }
                })
                .build()
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    try {
                        buildDatabase(appContext).also { INSTANCE = it }
                    } catch (e: Exception) {
                        // A corrupt or otherwise unopenable database (e.g. from a previous
                        // install, interrupted write, or schema mismatch the destructive
                        // fallback can't resolve) would otherwise crash the app on launch.
                        // Delete the bad file and rebuild from scratch so the app always opens.
                        try { appContext.deleteDatabase(DB_NAME) } catch (_: Exception) { }
                        val dbFile = File(appContext.getDatabasePath(DB_NAME).absolutePath)
                        if (dbFile.exists()) try { dbFile.delete() } catch (_: Exception) { }
                        buildDatabase(appContext).also { INSTANCE = it }
                    }
                }
            }
        }
    }
}
