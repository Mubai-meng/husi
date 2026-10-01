package com.fr.husi.database

/**
 * The object wrapper is must.
 * Otherwise, "[MissingType]: Element 'com.fr.husi.database.SagerDatabase' references a type that is not present"
 */
internal expect object SagerDatabaseProvider {
    fun create(): SagerDatabase
}
