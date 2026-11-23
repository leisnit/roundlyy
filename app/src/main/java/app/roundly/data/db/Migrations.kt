package app.roundly.data.db

import androidx.room.migration.Migration

/**
 * Migration policy. Alarms are user data and are never silently wiped, so there is no
 * destructive fallback on upgrade.
 *
 * 1. Bump [RoundlyDatabase.VERSION] and build. Room exports the new schema to app/schemas.
 * 2. For additive changes (new nullable column or table), prefer
 *    `@AutoMigration(from = N, to = N + 1)` in the @Database annotation.
 * 3. For anything else (renames, type changes, data rewrites), add a hand-written
 *    `Migration(N, N + 1)` to [ALL] and cover it with a MigrationTestHelper test in androidTest.
 *
 * A downgrade (for example a sideloaded older build) recreates the database. Users can protect
 * their data first with Settings > Export.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
