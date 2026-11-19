package com.personalfinance.tracker.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class QueryMigrationTest {
    @Test fun version12UpgradesWithHistoryAndPendingMessagesIntact() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "query-migration-test.db"
        context.deleteDatabase(name)
        val schema = javaClass.getResourceAsStream("/com.personalfinance.tracker.data.AppDatabase/12.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO bank_accounts VALUES (1, 'bank', 'account', '1234', 1000.1)")
                    for (i in 1..1000) db.execSQL("INSERT INTO transactions (id,amount,type,category,note,dateMillis,source,bankAccountId) VALUES (?,0.1,'EXPENSE','food','existing',?,'MANUAL',1)", arrayOf(i, i))
                    db.execSQL("INSERT INTO pending_sms (id,rawMessage,sender,timestampMillis,bankAccountId,status) VALUES (1,'existing','BANK',100,1,'PENDING')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try { helper.writableDatabase } finally { helper.close() }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_12_13).allowMainThreadQueries().build()
        try {
            val bundle = FinanceRepository(upgraded).exportAll()
            assertEquals(1000, bundle.transactions.size)
            assertEquals(1000.1, bundle.accounts.single().balance, 0.0)
            assertEquals("existing", bundle.pendingSms.single().rawMessage)
            upgraded.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM transactions WHERE parentTransactionId = 1").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(3).contains("index_transactions_parentTransactionId"))
            }
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
