/*
 * Schema 2 to 3: audio. Every field schema 3 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 2 to 3. Schema 3 added audio tracks and assets, clip volume and fades, and the audio commands;
 * schema 2 JSON has none of them and reads as clips at full volume without fades. The version bump exists so older
 * builds refuse projects that may contain audio instead of misreading them.
 */
object Migration2To3 : SchemaMigration {
    override val fromVersion: Int = 2

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
