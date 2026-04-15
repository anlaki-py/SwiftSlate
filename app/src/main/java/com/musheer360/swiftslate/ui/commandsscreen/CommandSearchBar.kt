package com.musheer360.swiftslate.ui.commandsscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.model.Command

/**
 * Search pill with query input, clear button, and expand/collapse-all toggle.
 *
 * @param searchQuery Current search text.
 * @param onQueryChange Callback when query text changes.
 * @param expandedIds Set of currently expanded command triggers.
 * @param filteredCommands The filtered command list for expand-all.
 * @param expandLabel Accessibility label for expanding.
 * @param collapseLabel Accessibility label for collapsing.
 * @param onToggleExpandAll Callback to toggle expand/collapse all.
 */
@Composable
internal fun CommandSearchBar(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    expandedIds: Set<String>,
    filteredCommands: List<Command>,
    expandLabel: String,
    collapseLabel: String,
    onToggleExpandAll: () -> Unit
) {
    val searchLabel = stringResource(R.string.commands_search_hint)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { contentDescription = searchLabel },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = searchLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            // Clear button — visible when there is a search query
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.commands_search_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(interactionSource = null, indication = null) {
                            onQueryChange("")
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Expand/collapse all toggle
            Icon(
                imageVector = if (expandedIds.isEmpty()) Icons.Default.List
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expandedIds.isEmpty()) expandLabel else collapseLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(interactionSource = null, indication = null) {
                        onToggleExpandAll()
                    }
            )
        }
    }
}
