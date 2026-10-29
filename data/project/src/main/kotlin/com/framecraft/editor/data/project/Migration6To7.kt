/*
 * Schema 6 to 7: transitions. Every field schema 7 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 6 to 7. Schema 7 added the transition after a clip and the commands that set one, set many, and
 * apply one to a whole track; schema 6 JSON has none of them and reads as hard cuts everywhere. The version bump
 * exists so older builds refuse projects that may carry transitions instead of dropping them.
 */
object Migration6To7 : SchemaMigration {
    override val fromVersion: Int = 6

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
