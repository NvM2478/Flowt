package com.flowt.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Category::class, TransactionEntity::class],
    version = 1,
    exportSchema = true, // schema JSON 导出到 app/schemas，迁移测试的依据
)
abstract class FlowtDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "flowt.db"

        @Volatile
        private var instance: FlowtDatabase? = null

        fun get(context: Context): FlowtDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlowtDatabase::class.java,
                    DB_NAME,
                )
                    // 刻意不调用 fallbackToDestructiveMigration()：
                    // 找不到迁移路径时宁可崩溃，也不静默清空用户账目。
                    .build()
                    .also { instance = it }
            }
    }
}
