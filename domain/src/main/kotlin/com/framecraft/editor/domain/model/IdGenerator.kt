/*
 * Source of stable, never-reused identifiers for projects, tracks, clips, and assets.
 */
package com.framecraft.editor.domain.model

import java.util.UUID

/** Produces unique identifiers. */
fun interface IdGenerator {
    /** Returns a new identifier that has never been returned before. */
    fun next(): String
}

/** Generates random UUID v4 identifiers. */
object UuidIdGenerator : IdGenerator {
    /** Returns a random UUID string. */
    override fun next(): String = UUID.randomUUID().toString()
}
