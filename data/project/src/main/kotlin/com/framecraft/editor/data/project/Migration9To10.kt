/*
 * Schema 9 to 10: masks and chroma keys. Every field schema 10 added has a default, so the JSON carries over
 * unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 9 to 10. Schema 10 added a clip's mask and chroma key and the commands that set them; schema 9 JSON
 * has none of them and reads as no mask and no key anywhere. The version bump exists so older builds refuse projects
 * that may carry them instead of dropping them.
 */
object Migration9To10 : SchemaMigration {
    override val fromVersion: Int = 9

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
