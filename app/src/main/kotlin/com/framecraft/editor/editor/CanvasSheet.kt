/*
 * The canvas sheet: the project's aspect ratio and what fills the canvas behind the base clips. Every tap is saved
 * as one edit right away, so the sheet has no Done button.
 */
package com.framecraft.editor.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.framecraft.editor.R
import com.framecraft.editor.domain.model.CanvasAspect
import com.framecraft.editor.domain.model.CanvasBackground
import com.framecraft.editor.domain.model.CanvasSettings
import com.framecraft.editor.ui.theme.Spacing

/** Test tag of the canvas sheet. */
const val CANVAS_SHEET_TAG = "canvas_sheet"

/** Test tag of the aspect tab. */
const val CANVAS_ASPECT_TAB_TAG = "aspect_tab"

/** Test tag of the background tab. */
const val CANVAS_BACKGROUND_TAB_TAG = "background_tab"

/** Test tag prefix of the aspect chips; `original` or the ratio as `width_height` follows. */
const val CANVAS_ASPECT_TAG_PREFIX = "aspect_chip_"

/** Test tag prefix of the background swatches; the background's name follows. */
const val CANVAS_BACKGROUND_TAG_PREFIX = "bg_swatch_"

/** Diameter of a background swatch. */
private val SWATCH_SIZE = 40.dp

/** Width of the ring around the chosen swatch. */
private val SWATCH_RING = 2.dp

/** The tabs of the canvas sheet. */
private enum class CanvasTab(
    @param:StringRes val labelRes: Int,
    val tag: String,
) {
    /** The aspect ratio chips. */
    ASPECT(R.string.editor_canvas_tab_aspect, CANVAS_ASPECT_TAB_TAG),

    /** The background swatches. */
    BACKGROUND(R.string.editor_canvas_tab_background, CANVAS_BACKGROUND_TAB_TAG),
}

/** A background the sheet offers: [background], [name] in tags, labeled [labelRes] and read as [descriptionRes]. */
private class BackgroundChoice(
    val background: CanvasBackground,
    val name: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
)

/** The backgrounds offered: black and blur first, then plain colors light to dark. */
private val BACKGROUNDS =
    listOf(
        BackgroundChoice(
            CanvasBackground.Black,
            "black",
            R.string.editor_canvas_bg_black,
            R.string.editor_canvas_bg_black_description,
        ),
        BackgroundChoice(
            CanvasBackground.Blur,
            "blur",
            R.string.editor_canvas_bg_blur,
            R.string.editor_canvas_bg_blur_description,
        ),
        BackgroundChoice(
            CanvasBackground.Solid(0xFFFFFFFF.toInt()),
            "white",
            R.string.editor_canvas_bg_white,
            R.string.editor_canvas_bg_white_description,
        ),
        BackgroundChoice(
            CanvasBackground.Solid(0xFF757575.toInt()),
            "gray",
            R.string.editor_canvas_bg_gray,
            R.string.editor_canvas_bg_gray_description,
        ),
        BackgroundChoice(
            CanvasBackground.Solid(0xFFE53935.toInt()),
            "red",
            R.string.editor_canvas_bg_red,
            R.string.editor_canvas_bg_red_description,
        ),
        BackgroundChoice(
            CanvasBackground.Solid(0xFF1E88E5.toInt()),
            "blue",
            R.string.editor_canvas_bg_blue,
            R.string.editor_canvas_bg_blue_description,
        ),
        BackgroundChoice(
            CanvasBackground.Solid(0xFF43A047.toInt()),
            "green",
            R.string.editor_canvas_bg_green,
            R.string.editor_canvas_bg_green_description,
        ),
    )

/** Returns the tag suffix of [aspect]: `original`, or its ratio as `width_height`. */
internal fun aspectTagOf(aspect: CanvasAspect): String =
    if (aspect == CanvasAspect.ORIGINAL) "original" else "${aspect.widthRatio}_${aspect.heightRatio}"

/**
 * Bottom sheet for [canvas]: a tap on an aspect chip goes to [onAspect] and a tap on a background swatch goes to
 * [onBackground], each saved as one edit; [onDismiss] closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanvasSheet(
    canvas: CanvasSettings,
    onAspect: (CanvasAspect) -> Unit,
    onBackground: (CanvasBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(CanvasTab.ASPECT) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(CANVAS_SHEET_TAG),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl)) {
            Text(
                text = stringResource(R.string.editor_canvas_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PrimaryTabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.padding(vertical = Spacing.md)) {
                CanvasTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(stringResource(entry.labelRes)) },
                        modifier = Modifier.testTag(entry.tag),
                    )
                }
            }
            when (tab) {
                CanvasTab.ASPECT -> AspectChoices(canvas.aspect, onAspect)
                CanvasTab.BACKGROUND -> BackgroundChoices(canvas.background, onBackground)
            }
        }
    }
}

/** One chip per canvas shape with [selected] chosen; picking one goes to [onPick]. */
@Composable
private fun AspectChoices(
    selected: CanvasAspect,
    onPick: (CanvasAspect) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CanvasAspect.entries.forEach { aspect ->
            val ratio = "${aspect.widthRatio}:${aspect.heightRatio}"
            val original = aspect == CanvasAspect.ORIGINAL
            val label = if (original) stringResource(R.string.editor_canvas_aspect_original) else ratio
            val description =
                if (original) {
                    stringResource(R.string.editor_canvas_aspect_original_description)
                } else {
                    stringResource(R.string.editor_canvas_aspect_description, ratio)
                }
            FilterChip(
                selected = aspect == selected,
                onClick = { onPick(aspect) },
                label = { Text(label) },
                modifier =
                    Modifier
                        .testTag(CANVAS_ASPECT_TAG_PREFIX + aspectTagOf(aspect))
                        .semantics { contentDescription = description },
            )
        }
    }
}

/** A row of labeled round swatches with [selected] ringed; tapping one sends its background to [onPick]. */
@Composable
private fun BackgroundChoices(
    selected: CanvasBackground,
    onPick: (CanvasBackground) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BACKGROUNDS.forEach { choice ->
            val chosen = choice.background == selected
            val description = stringResource(choice.descriptionRes)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .testTag(CANVAS_BACKGROUND_TAG_PREFIX + choice.name)
                        .clickable(role = Role.RadioButton) { onPick(choice.background) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = description
                            this.selected = chosen
                        },
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(SWATCH_SIZE)
                            .border(SWATCH_RING, if (chosen) colors.primary else colors.outline, CircleShape)
                            .padding(SWATCH_RING * 2)
                            .background(swatchBrush(choice.background), CircleShape),
                )
                Text(
                    text = stringResource(choice.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** Returns the fill of [background]'s swatch: its color, or for blur a soft gradient that hints at a blurred frame. */
private fun swatchBrush(background: CanvasBackground): Brush =
    when (background) {
        CanvasBackground.Black -> Brush.linearGradient(listOf(Color.Black, Color.Black))
        CanvasBackground.Blur -> Brush.linearGradient(BLUR_SWATCH_COLORS)
        is CanvasBackground.Solid -> Brush.linearGradient(listOf(Color(background.argb), Color(background.argb)))
    }

/** Colors of the blur swatch's gradient. */
private val BLUR_SWATCH_COLORS = listOf(Color(0xFF90A4AE), Color(0xFF5C6BC0), Color(0xFFF8BBD0))
