/*
 * Schema 1 to 2: overlays and anchors. Every field schema 2 added has a default, so the JSON carries over unchanged.
 */
package com.framecraft.editor.data.project

import kotlinx.serialization.json.JsonObject

/**
 * Upgrades schema 1 to 2. Schema 2 added overlay tracks, clip anchors, overlay placement, and anchor restorations;
 * schema 1 JSON has none of them and reads as clips without anchors. The version bump exists so older builds refuse
 * projects that may contain overlays instead of misreading them.
 */
object Migration1To2 : SchemaMigration {
    override val fromVersion: Int = 1

    /** Returns [project] unchanged. */
    override fun migrateProject(project: JsonObject): JsonObject = project

    /** Returns [command] unchanged. */
    override fun migrateCommand(command: JsonObject): JsonObject = command
}
