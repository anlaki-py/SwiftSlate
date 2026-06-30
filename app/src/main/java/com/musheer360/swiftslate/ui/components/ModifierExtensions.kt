package com.musheer360.swiftslate.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Clickable modifier that suppresses the default ripple indication.
 * Used for text fields that act as tap targets (e.g. model selector).
 *
 * @param onClick The action to perform on click.
 * @return A [Modifier] with click handling and no ripple.
 */
internal fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}
