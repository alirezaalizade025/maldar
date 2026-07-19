package com.personalfinance.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 7,
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
                        // Seed default categories directly against the database handed to
                        // this callback. Room fires onCreate lazily on the first DB access,
                        // which can be before the companion INSTANCE is assigned, so we must
                        // NOT depend on INSTANCE here (doing so silently skipped seeding).
                        seedDefaultCategories(db)
                    }
                })
                .build()
        }

        // Inserts the default expense/income categories using the raw database from
        // the Room onCreate callback. Runs synchronously on Room's own transaction
        // thread; onCreate only fires once, when the tables are first created.
        private fun seedDefaultCategories(db: SupportSQLiteDatabase) {
            try {
                val insert: (String, TxType) -> Unit = { name, type ->
                    db.execSQL(
                        "INSERT INTO categories (name, type) VALUES (?, ?)",
                        arrayOf<Any>(name, type.name)
                    )
                }
                defaultExpenseCategories.forEach { insert(it, TxType.EXPENSE) }
                defaultIncomeCategories.forEach { insert(it, TxType.INCOME) }
            } catch (_: Exception) {
                // Seeding is a convenience; never let it crash database creation.
            }
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
