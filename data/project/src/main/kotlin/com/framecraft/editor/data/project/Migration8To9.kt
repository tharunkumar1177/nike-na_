/*
 * Schema 8 to 9: voiceovers, ducking, and track volumes. Every field schema 9 added has a default, so the JSON
 * carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 8 to 9. Schema 9 added a track's volume, a clip's voiceover mark and ducking, the ducking field of
 * the clip sound command, the track volume of the lane-creating commands, and the command that sets track volumes;
 * schema 8 JSON has none of them and reads as unity volume and no ducking anywhere. The version bump exists so older
 * builds refuse projects that may carry them instead of dropping them.
 */
object Migration8To9 : SchemaMigration {
    override val fromVersion: Int = 8

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
