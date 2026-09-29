package com.oinky.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Early builds changed the schema while staying on version 1, so a "version 1" database on a
 * device may or may not already have the trip tables, `transactions.tripId`, or the sticker
 * tables. This migration checks what exists and adds only what's missing, keeping all data.
 *
 * The DDL mirrors what Room generates for the entities (it validates column names, types,
 * nullability, primary keys and indices after migrating).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MigrationSql.v1ToV2(
            hasColumn = { table, column -> db.hasColumn(table, column) },
            exec = db::execSQL,
        )
    }
}

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean =
    query("PRAGMA table_info(`$table`)").use { c ->
        val nameIdx = c.getColumnIndex("name")
        while (c.moveToNext()) if (c.getString(nameIdx) == column) return true
        false
    }

/** Plain SQL so it can be exercised against SQLite outside Android. */
object MigrationSql {
    val CREATE_TRIPS = "CREATE TABLE IF NOT EXISTS `trips` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, " +
        "`startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER NOT NULL, `localCurrency` TEXT NOT NULL, " +
        "`budgetMinor` INTEGER, `budgetCurrency` TEXT, `coverPhotoPath` TEXT, `note` TEXT NOT NULL, " +
        "`createdAt` INTEGER NOT NULL)"

    val CREATE_TRIP_PLACES = "CREATE TABLE IF NOT EXISTS `trip_places` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
        "`countryCode` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"

    val CREATE_STICKERS = "CREATE TABLE IF NOT EXISTS `stickers` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `path` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"

    val CREATE_DAY_STICKERS = "CREATE TABLE IF NOT EXISTS `day_stickers` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, " +
        "`stickerId` INTEGER NOT NULL, `rotation` REAL NOT NULL, `createdAt` INTEGER NOT NULL)"

    val INDICES = listOf(
        "CREATE INDEX IF NOT EXISTS `index_trip_places_tripId` ON `trip_places` (`tripId`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_tripId` ON `transactions` (`tripId`)",
        "CREATE INDEX IF NOT EXISTS `index_day_stickers_epochDay` ON `day_stickers` (`epochDay`)",
        "CREATE INDEX IF NOT EXISTS `index_day_stickers_stickerId` ON `day_stickers` (`stickerId`)",
    )

    fun v1ToV2(hasColumn: (table: String, column: String) -> Boolean, exec: (String) -> Unit) {
        exec(CREATE_TRIPS)
        exec(CREATE_TRIP_PLACES)
        if (!hasColumn("transactions", "tripId")) exec("ALTER TABLE `transactions` ADD COLUMN `tripId` INTEGER")
        exec(CREATE_STICKERS)
        exec(CREATE_DAY_STICKERS)
        INDICES.forEach(exec)
    }
}
