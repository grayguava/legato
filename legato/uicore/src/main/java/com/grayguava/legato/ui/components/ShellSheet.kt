package com.grayguava.legato.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The shell's sheet surface: [FloatingCard] with the sheet's own role filled in -
 * the content plus the parts a bottom sheet owns, so a new sheet takes the same
 * card behaviour instead of re-deriving it. The song menu keeps its own header, so
 * it passes that through with [header].
 */
@Composable
public fun ShellSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
    header: (@Composable ColumnScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    expandable: Boolean = false,
    startExpanded: Boolean = false,
    enter: AnimationSpec<Float>? = null,
    exit: AnimationSpec<Float>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FloatingCard(
        onDismiss = onDismiss,
        title = title,
        icon = icon,
        subtitle = subtitle,
        contentPadding = contentPadding,
        header = header,
        actions = actions,
        expandable = expandable,
        startExpanded = startExpanded,
        enter = enter,
        exit = exit,
        content = content,
    )
}
