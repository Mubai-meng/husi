package com.fr.husi.speedtest

/**
 * The object wrapper is must.
 * Otherwise, "[MissingType]: Element 'com.fr.husi.speedtest.SpeedTestDatabase' references a type that is not present"
 */
internal expect object SpeedTestDatabaseProvider {
    fun create(): SpeedTestDatabase
}
