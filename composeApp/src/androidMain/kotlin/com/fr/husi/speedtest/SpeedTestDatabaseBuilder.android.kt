package com.fr.husi.speedtest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fr.husi.repository.resolveAndroidRepository
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * SpeedTestDatabase 安卓实现 —— 独立数据库文件 speedtest.db，
 * 不与 SagerDatabase 共享，避免触碰核心建库/迁移代码。
 */
internal actual object SpeedTestDatabaseProvider {
    actual fun create(): SpeedTestDatabase {
        val repository = resolveAndroidRepository()
        val dbFile = File(repository.filesDir, "speedtest.db")
        dbFile.parentFile?.mkdirs()
        return Room.databaseBuilder<SpeedTestDatabase>(
            context = repository.context,
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
