/*
 * Schema 10 to 11: auto-captions. Every field schema 11 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 10 to 11. Schema 11 added a text clip's caption, a text style's highlight color, the captions a
 * trim restores, and the command that replaces captions; schema 10 JSON has none of them and reads as plain text
 * everywhere. The version bump exists so older builds refuse projects that may carry captions instead of dropping
 * them.
 */
object Migration10To11 : SchemaMigration {
    override val fromVersion: Int = 10

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
