/*
 * Upgrades stored JSON from older schema versions, one version at a time, before it is decoded.
 */
package com.framecraft.editor.data.project

import com.framecraft.editor.domain.model.CURRENT_SCHEMA_VERSION
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Upgrades JSON written by schema [fromVersion] to [fromVersion] + 1. Commands embed model types,
 * so a migration upgrades the project snapshot and every journaled command together.
 */
interface SchemaMigration {
    /** The schema version this step reads. */
    val fromVersion: Int

    /** Upgrades a serialized project. */
    fun migrateProject(project: JsonObject): JsonObject

    /** Upgrades a serialized edit command. */
    fun migrateCommand(command: JsonObject): JsonObject
}

/** The ordered chain of [steps] that brings any supported older schema to [CURRENT_SCHEMA_VERSION]. */
class SchemaMigrations(
    steps: List<SchemaMigration>,
) {
    private val stepsByVersion = steps.associateBy { it.fromVersion }

    /** Whether JSON written by [version] can be brought to the current schema. */
    fun canUpgrade(version: Int): Boolean = (version until CURRENT_SCHEMA_VERSION).all { it in stepsByVersion }

    /** Upgrades a serialized project from [version] and stamps it with the current schema version. */
    fun upgradeProject(
        project: JsonObject,
        version: Int,
    ): JsonObject {
        val upgraded = steps(version).fold(project) { json, step -> step.migrateProject(json) }
        return JsonObject(upgraded + (SCHEMA_VERSION_KEY to JsonPrimitive(CURRENT_SCHEMA_VERSION)))
    }

    /** Upgrades a serialized command from [version]. */
    fun upgradeCommand(
        command: JsonObject,
        version: Int,
    ): JsonObject = steps(version).fold(command) { json, step -> step.migrateCommand(json) }

    /** Returns the steps from [version] to the current schema. */
    private fun steps(version: Int): List<SchemaMigration> =
        (version until CURRENT_SCHEMA_VERSION).map { stepsByVersion.getValue(it) }

    /** Constants. */
    companion object {
        /** Key of the schema version inside a serialized project. */
        const val SCHEMA_VERSION_KEY = "schemaVersion"

        /** The chain shipped with this build, from schema 1 (the first) to the current schema. */
        val SHIPPED =
            SchemaMigrations(
                listOf(
                    Migration1To2,
                    Migration2To3,
                    Migration3To4,
                    Migration4To5,
                    Migration5To6,
                    Migration6To7,
                    Migration7To8,
                    Migration8To9,
                    Migration9To10,
                    Migration10To11,
                ),
            )
    }
}
