package com.personalfinance.tracker.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.robolectric.RuntimeEnvironment
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ShareMigrationTest {
    @Test fun migrationKeepsExistingTransactionAndDefaultsShareLinkToNull() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration
            .builder(RuntimeEnvironment.getApplication()).callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, amount REAL NOT NULL, note TEXT NOT NULL)")
                    db.execSQL("INSERT INTO transactions VALUES (7, 1234.0, 'existing')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_11_12.migrate(db)
            db.query("SELECT id, amount, note, parentTransactionId, smsTimestampMillis FROM transactions").use {
                assertTrue(it.moveToFirst())
                assertEquals(7L, it.getLong(0)); assertEquals(1234.0, it.getDouble(1), 0.0)
                assertEquals("existing", it.getString(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4))
            }
        } finally { helper.close() }
    }
}
