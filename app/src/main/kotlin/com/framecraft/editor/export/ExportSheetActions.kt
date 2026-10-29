/*
 * Callbacks of the export sheet.
 */
package com.framecraft.editor.export

import com.framecraft.editor.domain.export.ExportSettings

/** What the export sheet does: change settings, start, cancel, share or play the saved video, or close. */
class ExportSheetActions(
    val onSelect: (ExportSettings) -> Unit,
    val onStart: () -> Unit,
    val onCancel: () -> Unit,
    val onShare: (String) -> Unit,
    val onPlay: (String) -> Unit,
    val onClose: () -> Unit,
)
