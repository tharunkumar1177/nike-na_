/*
 * Schema 4 to 5: the canvas. Every field schema 5 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 4 to 5. Schema 5 added the project canvas (aspect and background), clip canvas placements, and the
 * canvas commands; schema 4 JSON has none of them and reads as an original-shape canvas on black with every base clip
 * fitted. The version bump exists so older builds refuse projects that may set a canvas instead of misreading them.
 */
object Migration4To5 : SchemaMigration {
    override val fromVersion: Int = 4

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
