package com.claudecodechat.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.claudecodechat.completion.CompletionItem
import com.claudecodechat.completion.CompletionState
import kotlinx.coroutines.launch
import com.intellij.ui.JBColor
import androidx.compose.ui.unit.IntOffset

/**
 * Completion popup that displays slash commands and file references
 */
@Composable
fun CompletionPopup(
    completionState: CompletionState,
    onItemClick: (CompletionItem) -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomStart,
    offset: IntOffset = IntOffset(12, -220)
) {
    if (!completionState.isShowing || !completionState.hasItems) {
        return
    }
    
    Popup(
        alignment = alignment,
        offset = offset,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        CompletionList(
            items = completionState.items,
            selectedIndex = completionState.selectedIndex,
            onItemClick = onItemClick,
            modifier = modifier
        )
    }
}

/**
 * The actual completion list content
 */
@Composable
private fun CompletionList(
    items: List<CompletionItem>,
    selectedIndex: Int,
    onItemClick: (CompletionItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Scroll to selected item when it changes
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            coroutineScope.launch {
                listState.animateScrollToItem(selectedIndex)
            }
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(2.dp)
        ) {
            itemsIndexed(items) { index, item ->
                CompactCompletionItem(
                    item = item,
                    isSelected = index == selectedIndex,
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Compact completion item - one line format: command - description
 */
@Composable
private fun CompactCompletionItem(
    item: CompletionItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        Color(0xFF007ACC).copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Command/file name
        SimpleText(
            text = when (item) {
                is CompletionItem.SlashCommand -> item.fullCommand
                is CompletionItem.FileReference -> item.fileName
            },
            color = if (isSelected) Color(0xFF007ACC) else Color(JBColor.foreground().rgb),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
        )
        
        // Separator
        SimpleText(
            text = " - ",
            color = Color(JBColor.foreground().rgb).copy(alpha = 0.5f),
            fontSize = 13.sp,
            maxLines = 1
        )
        
        // Description
        SimpleText(
            text = when (item) {
                is CompletionItem.SlashCommand -> item.description
                is CompletionItem.FileReference -> item.relativePath
            },
            color = Color(JBColor.foreground().rgb).copy(alpha = 0.7f),
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        
        // Source indicator for non-built-in commands
        if (item is CompletionItem.SlashCommand && item.source != com.claudecodechat.completion.CompletionSource.BUILT_IN) {
            SimpleText(
                text = when (item.source) {
                    com.claudecodechat.completion.CompletionSource.PROJECT -> "📁"
                    com.claudecodechat.completion.CompletionSource.USER -> "👤"
                    com.claudecodechat.completion.CompletionSource.MCP -> "🔌"
                    else -> ""
                },
                color = Color.Unspecified,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * Custom card component for the completion popup
 */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(JBColor.background().rgb))
            .border(
                1.dp, 
                Color(JBColor.border().rgb),
                RoundedCornerShape(8.dp)
            )
            .padding(4.dp)
    ) {
        content()
    }
}

/**
 * Simple text component matching the main project's style
 */
@Composable
private fun SimpleText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}