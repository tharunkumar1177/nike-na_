/*
 * Schema 5 to 6: clip speed. Every field schema 6 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 5 to 6. Schema 6 added clip speed, source length, and pitch, the source length on trims and splits,
 * the speed on merges, and the speed command; schema 5 JSON has none of them and reads as every clip at normal speed.
 * The version bump exists so older builds refuse projects that may speed clips instead of misreading them.
 */
object Migration5To6 : SchemaMigration {
    override val fromVersion: Int = 5

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
