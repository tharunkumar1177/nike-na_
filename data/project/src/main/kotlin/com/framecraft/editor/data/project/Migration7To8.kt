/*
 * Schema 7 to 8: looks. Every field schema 8 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 7 to 8. Schema 8 added a clip's look and the commands that set one look, set many, and apply one
 * to every picture clip; schema 7 JSON has none of them and reads as no look anywhere. The version bump exists so
 * older builds refuse projects that may carry looks instead of dropping them.
 */
object Migration7To8 : SchemaMigration {
    override val fromVersion: Int = 7

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
