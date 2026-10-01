package com.fr.husi.speedtest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fr.husi.repository.resolveRepository
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * SpeedTestDatabase 桌面实现 —— 独立数据库文件 speedtest.db。
 */
internal actual object SpeedTestDatabaseProvider {
    actual fun create(): SpeedTestDatabase {
        val dbFile = File(resolveRepository().filesDir, "speedtest.db")
        dbFile.parentFile?.mkdirs()
        return Room.databaseBuilder<SpeedTestDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
