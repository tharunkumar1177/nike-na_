/*
 * Schema 3 to 4: text. Every field schema 4 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 3 to 4. Schema 4 added text tracks, the text asset, clip text, text animation fields on trims and
 * merges, and the text commands; schema 3 JSON has none of them and reads as projects without text. The version bump
 * exists so older builds refuse projects that may contain text instead of misreading them.
 */
object Migration3To4 : SchemaMigration {
    override val fromVersion: Int = 3

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
