package com.claudecodechat.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
// TODO: Re-enable when Jewel bundled modules are available
// import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import com.claudecodechat.completion.CompletionManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.claudecodechat.models.ClaudeStreamMessage
import com.claudecodechat.models.SessionMetrics
import com.claudecodechat.models.ContentType
import com.claudecodechat.models.MessageType
import com.claudecodechat.state.SessionViewModel
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.openapi.fileEditor.FileEditorManager
import androidx.compose.runtime.DisposableEffect
import javax.swing.JComponent
import kotlin.math.sin
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.util.ast.NodeVisitor
import com.vladsch.flexmark.util.ast.VisitHandler
import kotlin.math.PI
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonElement

/**
 * Final version of Claude Chat Panel with all improvements
 */
class ClaudeChatPanelFinal(private val project: Project) {
    
    private val viewModel = SessionViewModel(project)
    private var lastSentMessage = mutableStateOf("")
    
    init {
        com.intellij.openapi.diagnostic.Logger.getInstance(ClaudeChatPanelFinal::class.java)
            .info("Initializing Claude Chat Panel for project: ${project.name}")
    }
    
    fun createComponent(): JComponent {
        val chatPanel = ComposePanel()
        chatPanel.setContent {
            ClaudeChatContent()
        }
        return chatPanel
    }
    
    @Composable
    private fun ClaudeChatContent() {
        // TODO: Wrap with SwingBridgeTheme when available
        // SwingBridgeTheme {
            val messages by viewModel.messages.collectAsState()
            val isLoading by viewModel.isLoading.collectAsState()
            val currentSession by viewModel.currentSession.collectAsState()
            val metrics by viewModel.sessionMetrics.collectAsState()
            
            // Completion state
            val completionManager = remember { com.claudecodechat.completion.CompletionManager(project) }
            val completionState by completionManager.completionState.collectAsState()
            var inputTextValue by remember { mutableStateOf(TextFieldValue("")) }
        
        // Log loading state changes
        LaunchedEffect(isLoading) {
            com.intellij.openapi.diagnostic.Logger.getInstance(ClaudeChatPanelFinal::class.java)
                .info("UI Loading state changed to: $isLoading")
        }
        
        // Adaptive theme colors
        val isDarkTheme = JBColor.isBright().not()
        val backgroundColor = if (isDarkTheme) Color(0xFF2B2D30) else Color(0xFFF7F7F7)
        val surfaceColor = if (isDarkTheme) Color(0xFF3C3F41) else Color.White
        val textColor = if (isDarkTheme) Color(0xFFBBBBBB) else Color(0xFF2B2B2B)
        val primaryColor = Color(0xFF4A9EFF)
        val errorColor = Color(0xFFEF5350)
        val inputBgColor = if (isDarkTheme) Color(0xFF2B2D30) else Color.White
        val borderColor = if (isDarkTheme) Color(0xFF4A9EFF).copy(alpha = 0.6f) else Color(0xFF4A9EFF).copy(alpha = 0.4f)
        val codeBlockBg = if (isDarkTheme) Color(0xFF1E1F22) else Color(0xFFF5F5F5)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            // Main chat content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
            // Session Info Bar
            SessionInfoBar(
                session = currentSession,
                metrics = metrics,
                onNewSession = { viewModel.startNewSession() },
                onResumeSession = { sessionId -> viewModel.resumeSession(sessionId) },
                textColor = textColor,
                primaryColor = primaryColor,
                backgroundColor = backgroundColor,
                surfaceColor = surfaceColor,
                isDarkTheme = isDarkTheme
            )
            
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(textColor.copy(alpha = 0.2f))
                    .padding(vertical = 8.dp)
            )
            
            // Messages List
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                ProcessedMessageList(
                    messages = messages,
                    modifier = Modifier.fillMaxSize(),
                    textColor = textColor,
                    primaryColor = primaryColor,
                    errorColor = errorColor,
                    backgroundColor = backgroundColor,
                    surfaceColor = surfaceColor,
                    codeBlockBg = codeBlockBg,
                    isDarkTheme = isDarkTheme
                )
            }
            
            // Claude loading indicator above input with last message summary
            if (isLoading) {
                ClaudeLoadingIndicator(
                    textColor = textColor,
                    primaryColor = primaryColor,
                    errorColor = errorColor,
                    isDarkTheme = isDarkTheme,
                    lastMessage = lastSentMessage.value
                )
            }
            
            

            // Completion popup positioned just above input area
            if (completionState.isShowing) {
                CompletionPopup(
                    completionState = completionState,
                    onItemClick = { item ->
                        val result = completionManager.acceptCompletion(
                            inputTextValue.text,
                            inputTextValue.selection.end
                        )
                        if (result != null) {
                            inputTextValue = TextFieldValue(
                                text = result.newText,
                                selection = TextRange(result.newCursorPosition)
                            )
                        }
                    },
                    alignment = Alignment.BottomStart,
                    offset = IntOffset(24, -280)
                )
            }

            // Input Area
            InputArea(
                enabled = !isLoading,
                isLoading = isLoading,
                inputTextValue = inputTextValue,
                onInputChange = { newValue ->
                    inputTextValue = newValue
                    completionManager.updateCompletion(newValue.text, newValue.selection.end)
                },
                completionManager = completionManager,
                completionState = completionState,
                onSendMessage = { prompt, model ->
                    // Build structured message with IDE context
                    val finalMessage = buildString {
                        append(prompt)
                        
                        // Append IDE context if available
                        val contextParts = mutableListOf<String>()
                        
                        // Add current file context
                        val fileEditorManager = FileEditorManager.getInstance(project)
                        val currentEditor = fileEditorManager.selectedEditor
                        val currentFile = currentEditor?.file
                        if (currentFile != null) {
                            contextParts.add("User is in IDE and has ${currentFile.presentableName} open")
                        }
                        
                        // Add selected lines context
                        val textEditor = fileEditorManager.selectedTextEditor
                        if (textEditor != null && textEditor.selectionModel.hasSelection()) {
                            val startLine = textEditor.document.getLineNumber(textEditor.selectionModel.selectionStart) + 1
                            val endLine = textEditor.document.getLineNumber(textEditor.selectionModel.selectionEnd) + 1
                            if (startLine == endLine) {
                                contextParts.add("User has selected line $startLine")
                            } else {
                                contextParts.add("User has selected lines $startLine-$endLine")
                            }
                            
                            // Include selected text
                            val selectedText = textEditor.selectionModel.selectedText
                            if (!selectedText.isNullOrBlank()) {
                                append("\n\nSelected code:\n```\n")
                                append(selectedText)
                                append("\n```")
                            }
                        }
                        
                        // Add context as structured metadata
                        if (contextParts.isNotEmpty()) {
                            append("\n\n[IDE Context: ")
                            append(contextParts.joinToString(", "))
                            append("]")
                        }
                    }
                    
                    com.intellij.openapi.diagnostic.Logger.getInstance(ClaudeChatPanelFinal::class.java)
                        .info("Sending message from UI: $finalMessage")
                    lastSentMessage.value = finalMessage
                    viewModel.sendPrompt(finalMessage, model)
                    inputTextValue = TextFieldValue("")
                    completionManager.hideCompletion()
                },
                onStop = { viewModel.stopCurrentRequest() },
                textColor = textColor,
                primaryColor = primaryColor,
                backgroundColor = backgroundColor,
                inputBgColor = inputBgColor,
                borderColor = borderColor,
                isDarkTheme = isDarkTheme
            )
            } // End of Column
        } // End of Box
        // } // End of SwingBridgeTheme (TODO: re-enable)
    }
    
    @Composable
    private fun ClaudeLoadingIndicator(
        textColor: Color,
        primaryColor: Color,
        errorColor: Color,
        isDarkTheme: Boolean,
        lastMessage: String = ""
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
        
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Animated Claude icon
                    ClaudeIcon(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFFD97757),
                        alpha = pulseAlpha
                    )
                    
                    Column {
                        SimpleText(
                            text = "Claude is thinking...",
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 13.sp
                        )
                        if (lastMessage.isNotEmpty()) {
                            // Extract just the first line of the message, excluding IDE context
                            val cleanMessage = lastMessage
                                .substringBefore("\n\nSelected code:")
                                .substringBefore("\n\n[IDE Context:")
                                .lines()
                                .firstOrNull()
                                ?.trim() ?: ""
                            
                            if (cleanMessage.isNotEmpty()) {
                                val summary = if (cleanMessage.length > 50) 
                                    cleanMessage.take(47) + "..." 
                                else cleanMessage
                                SimpleText(
                                    text = summary,
                                    color = textColor.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            
                            // Show IDE context indicator separately if present
                            val contextPattern = "\\[IDE Context: ([^\\]]+)\\]".toRegex()
                            val contextMatch = contextPattern.find(lastMessage)
                            if (contextMatch != null) {
                                SimpleText(
                                    text = "[IDE Context: User is in...]",
                                    color = textColor.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Animated progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(textColor.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.3f),
                                    primaryColor,
                                    primaryColor.copy(alpha = 0.3f)
                                ),
                                startX = progress * 1000,
                                endX = (progress + 0.3f) * 1000
                            )
                        )
                )
            }
        }
    }
    
    @Composable
    private fun SessionInfoBar(
        session: SessionViewModel.SessionInfo?,
        metrics: SessionMetrics,
        onNewSession: () -> Unit,
        onResumeSession: (String) -> Unit,
        textColor: Color,
        primaryColor: Color,
        backgroundColor: Color,
        surfaceColor: Color,
        isDarkTheme: Boolean
    ) {
        val recentSessionsWithDetails = remember { viewModel.getRecentSessionsWithDetails() }
        val recentSessions = remember { recentSessionsWithDetails.map { it.id } }
        var showResumeDropdown by remember { mutableStateOf(false) }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    session?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SimpleText(
                                text = "Session:",
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            SimpleText(
                                text = it.sessionId.take(8),
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row {
                            SimpleText(
                                text = "${metrics.totalInputTokens} in, ${metrics.totalOutputTokens} out",
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                            if (metrics.toolsExecuted > 0) {
                                SimpleText(
                                    text = " • ${metrics.toolsExecuted} tools",
                                    color = textColor.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } ?: Column {
                        SimpleText(
                            text = "No active session",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                        if (recentSessions.isNotEmpty()) {
                            SimpleText(
                                text = "Recent sessions available",
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // New Session button
                    IntelliJButton(
                        text = "New Session",
                        onClick = onNewSession,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        isDefault = false
                    )
                    
                    // Resume button with dropdown
                    if (recentSessions.isNotEmpty()) {
                        Box {
                            IntelliJButton(
                                text = "Resume",
                                onClick = { 
                                    if (recentSessions.size == 1) {
                                        onResumeSession(recentSessions.first())
                                    } else {
                                        showResumeDropdown = !showResumeDropdown
                                    }
                                },
                                textColor = textColor,
                                backgroundColor = backgroundColor,
                                isDefault = false,
                                hasDropdown = recentSessions.size > 1
                            )
                            
                            if (showResumeDropdown && recentSessionsWithDetails.size > 1) {
                                Popup(
                                    alignment = Alignment.TopEnd,
                                    offset = IntOffset(0, 30),
                                    onDismissRequest = { showResumeDropdown = false }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .width(350.dp)  // Wider for better display
                                            .heightIn(max = 400.dp)  // Max height with scrolling
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isDarkTheme) Color(0xFF2B2D30) else Color.White)
                                            .border(
                                                1.dp, 
                                                if (isDarkTheme) Color(0xFF3C3F41) else Color(0xFFD1D1D1),
                                                RoundedCornerShape(6.dp)
                                            )
                                    ) {
                                        Column(
                                            modifier = Modifier.verticalScroll(rememberScrollState())
                                        ) {
                                            recentSessionsWithDetails.forEach { sessionDetails ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onResumeSession(sessionDetails.id)
                                                            showResumeDropdown = false
                                                        }
                                                        .then(
                                                            if (isDarkTheme)
                                                                Modifier.hoverable(remember { MutableInteractionSource() })
                                                            else
                                                                Modifier
                                                        )
                                                        .padding(12.dp)
                                                ) {
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        // First line: Session ID and timestamp
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                SimpleText(
                                                                    text = sessionDetails.id.take(8),
                                                                    color = primaryColor,
                                                                    fontSize = 12.sp,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                                SimpleText(
                                                                    text = "(${sessionDetails.messageCount} msgs)",
                                                                    color = textColor.copy(alpha = 0.5f),
                                                                    fontSize = 11.sp
                                                                )
                                                            }
                                                            SimpleText(
                                                                text = sessionDetails.timestamp,
                                                                color = textColor.copy(alpha = 0.5f),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                        
                                                        // Second line: Preview text
                                                        SimpleText(
                                                            text = sessionDetails.preview.take(80) + 
                                                                  if (sessionDetails.preview.length > 80) "..." else "",
                                                            color = textColor.copy(alpha = 0.7f),
                                                            fontSize = 12.sp,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                                
                                                // Divider between items
                                                if (sessionDetails != recentSessionsWithDetails.last()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(1.dp)
                                                            .background(textColor.copy(alpha = 0.1f))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ProcessedMessageList(
        messages: List<ClaudeStreamMessage>,
        modifier: Modifier = Modifier,
        textColor: Color,
        primaryColor: Color,
        errorColor: Color,
        backgroundColor: Color,
        surfaceColor: Color,
        codeBlockBg: Color,
        isDarkTheme: Boolean
    ) {
        // Process messages to merge tool results with tool calls
        val processedMessages = remember(messages) {
            val result = mutableListOf<ProcessedMessage>()
            val toolResultsMap = mutableMapOf<String, MutableList<com.claudecodechat.models.Content>>()
            
            // First pass: collect tool results by tool_use_id
            messages.forEach { message ->
                message.message?.content?.forEach { content ->
                    if (content.type == ContentType.TOOL_RESULT && content.toolUseId != null) {
                        toolResultsMap.getOrPut(content.toolUseId) { mutableListOf() }.add(content)
                    }
                }
            }
            
            // Second pass: process messages and merge tool results
            messages.forEach { message ->
                // Skip system, result, and meta messages
                if (message.type == MessageType.SYSTEM || 
                    message.type.name == "RESULT" || 
                    message.subtype == "result" ||
                    message.isMeta) {
                    return@forEach
                }
                
                // Skip messages that only contain tool results
                val hasOnlyToolResults = message.message?.content?.all { 
                    it.type == ContentType.TOOL_RESULT 
                } ?: false
                if (hasOnlyToolResults) {
                    return@forEach
                }
                
                result.add(ProcessedMessage(
                    original = message,
                    toolResults = message.message?.content?.mapNotNull { content ->
                        if (content.type == ContentType.TOOL_USE && content.id != null) {
                            toolResultsMap[content.id]
                        } else null
                    }?.flatten() ?: emptyList()
                ))
            }
            
            result
        }
        
        val listState = rememberLazyListState()
        
        LaunchedEffect(processedMessages.size) {
            if (processedMessages.isNotEmpty()) {
                listState.animateScrollToItem(processedMessages.size - 1)
            }
        }
        
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(processedMessages) { processedMessage ->
                ProcessedMessageCard(
                    processedMessage = processedMessage,
                    textColor = textColor,
                    primaryColor = primaryColor,
                    errorColor = errorColor,
                    backgroundColor = backgroundColor,
                    surfaceColor = surfaceColor,
                    codeBlockBg = codeBlockBg,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
    
    data class ProcessedMessage(
        val original: ClaudeStreamMessage,
        val toolResults: List<com.claudecodechat.models.Content>
    )
    
    @Composable
    private fun ProcessedMessageCard(
        processedMessage: ProcessedMessage,
        textColor: Color,
        primaryColor: Color,
        errorColor: Color,
        backgroundColor: Color,
        surfaceColor: Color,
        codeBlockBg: Color,
        isDarkTheme: Boolean
    ) {
        val message = processedMessage.original
        val isAssistant = message.type == MessageType.ASSISTANT
        val isUser = message.type == MessageType.USER
        val isError = message.type == MessageType.ERROR
        
        val cardModifier = when {
            isAssistant -> Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 4.dp)
            isUser -> Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isDarkTheme) primaryColor.copy(alpha = 0.1f) else primaryColor.copy(alpha = 0.05f))
                .padding(12.dp)
            isError -> Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(errorColor.copy(alpha = 0.1f))
                .border(1.dp, errorColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .padding(12.dp)
            else -> Modifier
                .fillMaxWidth()
                .padding(12.dp)
        }
        
        Box(modifier = cardModifier) {
            Column {
                // Only show header for user messages and errors (no icons)
                if (message.type == MessageType.USER || message.type == MessageType.ERROR) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SimpleText(
                            text = when(message.type) {
                                MessageType.USER -> "You"
                                MessageType.ERROR -> "Error"
                                else -> message.type.name
                            },
                            color = when(message.type) {
                                MessageType.ERROR -> errorColor
                                else -> textColor.copy(alpha = 0.7f)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        message.timestamp?.let {
                            SimpleText(
                                text = it.substring(11, 19),
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                
                // Message content
                if (message.type == MessageType.ASSISTANT) {
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            message.message?.content?.forEach { content ->
                                when (content.type) {
                                    ContentType.TEXT -> {
                                        content.text?.let { text ->
                                            // Check if the message contains IDE context (for user messages)
                                            val contextPattern = "\\[IDE Context: ([^\\]]+)\\]".toRegex()
                                            val contextMatch = contextPattern.find(text)
                                            
                                            if (contextMatch != null && message.type == MessageType.USER) {
                                                // Split text into main content and context
                                                val mainText = text.substring(0, contextMatch.range.first).trim()
                                                val contextText = contextMatch.groupValues[1]
                                                
                                                Column {
                                                    // Display main message
                                                    if (mainText.isNotEmpty()) {
                                                        MarkdownText(
                                                            text = mainText,
                                                            textColor = textColor,
                                                            primaryColor = primaryColor,
                                                            codeBlockBg = codeBlockBg
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }
                                                    
                                                    // Display IDE context with secondary color
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(textColor.copy(alpha = 0.05f))
                                                            .padding(8.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            SimpleText(
                                                                text = "ℹ",
                                                                color = textColor.copy(alpha = 0.5f),
                                                                fontSize = 12.sp
                                                            )
                                                            SimpleText(
                                                                text = contextText,
                                                                color = textColor.copy(alpha = 0.6f),
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                MarkdownText(
                                                    text = text,
                                                    textColor = textColor,
                                                    primaryColor = primaryColor,
                                                    codeBlockBg = codeBlockBg
                                                )
                                            }
                                        }
                                    }
                                    ContentType.TOOL_USE -> {
                                        Column {
                                            ToolUseCard(
                                                toolName = content.name ?: "Unknown Tool",
                                                toolId = content.id ?: "",
                                                input = content.input,
                                                textColor = textColor,
                                                primaryColor = primaryColor,
                                                codeBlockBg = codeBlockBg
                                            )
                                            
                                            // Show associated tool results below tool call
                                            val toolResults = processedMessage.toolResults.filter { 
                                                it.toolUseId == content.id 
                                            }
                                            if (toolResults.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                toolResults.forEach { result ->
                                                    ToolResultCard(
                                                        content = result.content ?: "",
                                                        isError = result.isError ?: false,
                                                        textColor = textColor,
                                                        errorColor = errorColor,
                                                        primaryColor = primaryColor,
                                                        codeBlockBg = codeBlockBg,
                                                        isNested = true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    ContentType.TOOL_RESULT -> {
                                        // Skip tool results here as they're shown with tool calls
                                    }
                                    else -> {}
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                } else {
                    // Non-assistant messages
                    SelectionContainer {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            message.message?.content?.forEach { content ->
                                when (content.type) {
                                    ContentType.TEXT -> {
                                        content.text?.let { text ->
                                            // Check if the message contains IDE context (for user messages)
                                            val contextPattern = "\\[IDE Context: ([^\\]]+)\\]".toRegex()
                                            val contextMatch = contextPattern.find(text)
                                            
                                            if (contextMatch != null && message.type == MessageType.USER) {
                                                // Split text into main content and context
                                                val mainText = text.substring(0, contextMatch.range.first).trim()
                                                val contextText = contextMatch.groupValues[1]
                                                
                                                Column {
                                                    // Display main message
                                                    if (mainText.isNotEmpty()) {
                                                        MarkdownText(
                                                            text = mainText,
                                                            textColor = textColor,
                                                            primaryColor = primaryColor,
                                                            codeBlockBg = codeBlockBg
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }
                                                    
                                                    // Display IDE context with secondary color
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(textColor.copy(alpha = 0.05f))
                                                            .padding(8.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            SimpleText(
                                                                text = "ℹ",
                                                                color = textColor.copy(alpha = 0.5f),
                                                                fontSize = 12.sp
                                                            )
                                                            SimpleText(
                                                                text = contextText,
                                                                color = textColor.copy(alpha = 0.6f),
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                MarkdownText(
                                                    text = text,
                                                    textColor = textColor,
                                                    primaryColor = primaryColor,
                                                    codeBlockBg = codeBlockBg
                                                )
                                            }
                                        }
                                    }
                                    ContentType.TOOL_USE -> {
                                        ToolUseCard(
                                            toolName = content.name ?: "Unknown Tool",
                                            toolId = content.id ?: "",
                                            input = content.input,
                                            textColor = textColor,
                                            primaryColor = primaryColor,
                                            codeBlockBg = codeBlockBg
                                        )
                                    }
                                    ContentType.TOOL_RESULT -> {
                                        ToolResultCard(
                                            content = content.content ?: "",
                                            isError = content.isError ?: false,
                                            textColor = textColor,
                                            errorColor = errorColor,
                                            primaryColor = primaryColor,
                                            codeBlockBg = codeBlockBg
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
                
                // Error info
                message.error?.let { error ->
                    ErrorCard(error, textColor, errorColor)
                }
            }
        }
    }
    
    
    data class TodoItem(
        val content: String,
        val status: String,
        val activeForm: String?
    )
    
    private fun parseTodoWriteInput(input: Any?): List<TodoItem> {
        if (input == null) return emptyList()
        
        return try {
            val jsonStr = input.toString()
            // Try to parse the input as JSON
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            
            // Check if it's wrapped in {todos: [...]} or just [...]
            val todosArray = if (jsonStr.trim().startsWith("{") && jsonStr.contains("\"todos\"")) {
                val obj = json.parseToJsonElement(jsonStr).jsonObject
                obj["todos"]?.jsonArray ?: JsonArray(emptyList())
            } else if (jsonStr.contains("todos=")) {
                // Handle Kotlin toString() format
                extractTodosFromString(jsonStr)
                    .map { todo ->
                        buildJsonObject {
                            put("content", todo.content)
                            put("status", todo.status)
                            todo.activeForm?.let { put("activeForm", it) }
                        }
                    }.let { JsonArray(it) }
            } else {
                json.parseToJsonElement(jsonStr).jsonArray
            }
            
            todosArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    TodoItem(
                        content = obj["content"]?.jsonPrimitive?.content ?: "",
                        status = obj["status"]?.jsonPrimitive?.content ?: "pending",
                        activeForm = obj["activeForm"]?.jsonPrimitive?.content
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            // If JSON parsing fails, try to extract from toString format
            extractTodosFromString(input.toString())
        }
    }
    
    private fun extractTodosFromString(str: String): List<TodoItem> {
        // Fallback parser for Kotlin data class toString() format
        val todoRegex = """content=([^,}]+),\s*status=([^,}]+)(?:,\s*activeForm=([^}]+))?""".toRegex()
        return todoRegex.findAll(str).map { match ->
            TodoItem(
                content = match.groupValues[1].trim('"', ' '),
                status = match.groupValues[2].trim('"', ' '),
                activeForm = match.groupValues.getOrNull(3)?.trim('"', ' ', ')', 'n', 'u', 'l')
                    ?.takeIf { it != "ull" && it.isNotEmpty() }
            )
        }.toList()
    }
    
    @Composable
    private fun TodoWriteContent(
        input: Any?,
        textColor: Color,
        primaryColor: Color,
        errorColor: Color
    ) {
        val todos = parseTodoWriteInput(input)
        if (todos.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                todos.forEach { todo ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox icon based on status
                        val (icon, color) = when (todo.status) {
                            "completed" -> "✓" to primaryColor
                            "in_progress" -> "⟳" to Color(0xFFFFB347)
                            else -> "☐" to textColor.copy(alpha = 0.6f)
                        }
                        SimpleText(
                            text = icon,
                            color = color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Task content or activeForm based on status
                        SimpleText(
                            text = if (todo.status == "in_progress") {
                                todo.activeForm ?: todo.content
                            } else {
                                todo.content
                            },
                            color = when (todo.status) {
                                "completed" -> textColor.copy(alpha = 0.6f)
                                "in_progress" -> textColor
                                else -> textColor.copy(alpha = 0.8f)
                            },
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // Fallback to raw display if parsing fails
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(textColor.copy(alpha = 0.05f))
                    .border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                SimpleText(
                    text = formatToolInput(input),
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    
    @Composable
    private fun FileWriteToolDisplay(
        toolName: String,
        input: Any?,
        textColor: Color,
        primaryColor: Color,
        codeBlockBg: Color
    ) {
        val inputStr = input?.toString() ?: ""
        val filePath = extractParameter(inputStr, "file_path")
        val content = extractParameter(inputStr, "content")
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // File path display
            if (filePath.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SimpleText(
                        text = "File:",
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    SimpleText(
                        text = filePath,
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Content in code editor style
            if (content.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(codeBlockBg)
                        .border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        SimpleText(
                            text = content,
                            color = textColor.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
    
    private fun extractParameter(input: String, param: String): String {
        val regex = """$param=([^,}]+)""".toRegex()
        return regex.find(input)?.groupValues?.getOrNull(1)
            ?.trim('"', ' ', '\n')
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"") ?: ""
    }
    
    @Composable
    private fun EditToolContent(
        input: Any?,
        textColor: Color,
        primaryColor: Color,
        codeBlockBg: Color,
        isExpanded: Boolean,
        onToggleExpanded: () -> Unit
    ) {
        // Extract old_string and new_string from input
        val (oldString, newString) = when (input) {
            is JsonElement -> {
                try {
                    val obj = input.jsonObject
                    val old = obj["old_string"]?.jsonPrimitive?.content ?: ""
                    val new = obj["new_string"]?.jsonPrimitive?.content ?: ""
                    old to new
                } catch (e: Exception) {
                    val old = extractParameter(input.toString(), "old_string")
                    val new = extractParameter(input.toString(), "new_string")
                    old to new
                }
            }
            else -> {
                val old = extractParameter(input.toString(), "old_string")
                val new = extractParameter(input.toString(), "new_string")
                old to new
            }
        }
        
        // Process lines for diff display
        val oldLines = oldString.lines()
        val newLines = newString.lines()
        val maxLines = maxOf(oldLines.size, newLines.size)
        
        // Determine if we need expand/collapse
        val shouldShowToggle = maxLines > 5
        val displayLineCount = if (isExpanded || !shouldShowToggle) maxLines else 3
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 4.dp)
        ) {
            // Show diff-like display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(codeBlockBg.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Show removed lines
                    if (oldString.isNotEmpty()) {
                        val linesToShow = if (isExpanded || !shouldShowToggle) oldLines else oldLines.take(displayLineCount)
                        linesToShow.forEachIndexed { index, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SimpleText(
                                    text = "-",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                SimpleText(
                                    text = line.ifEmpty { " " },
                                    color = Color(0xFFFF6B6B).copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (!isExpanded && shouldShowToggle && oldLines.size > displayLineCount) {
                            SimpleText(
                                text = "  ... ${oldLines.size - displayLineCount} more lines removed",
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    // Add spacing between old and new if both exist
                    if (oldString.isNotEmpty() && newString.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // Show added lines
                    if (newString.isNotEmpty()) {
                        val linesToShow = if (isExpanded || !shouldShowToggle) newLines else newLines.take(displayLineCount)
                        linesToShow.forEachIndexed { index, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SimpleText(
                                    text = "+",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                SimpleText(
                                    text = line.ifEmpty { " " },
                                    color = Color(0xFF4CAF50).copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (!isExpanded && shouldShowToggle && newLines.size > displayLineCount) {
                            SimpleText(
                                text = "  ... ${newLines.size - displayLineCount} more lines added",
                                color = textColor.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            
            // Show more/less toggle if needed
            if (shouldShowToggle) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.clickable { onToggleExpanded() }
                ) {
                    SimpleText(
                        text = if (isExpanded) "show less" else "show more",
                        color = primaryColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
    
    @Composable
    private fun ToolUseCard(
        toolName: String,
        toolId: String,
        input: Any? = null,
        textColor: Color,
        primaryColor: Color,
        codeBlockBg: Color
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        
        // Determine tool type for special rendering
        val isToDoWrite = toolName.contains("TodoWrite", ignoreCase = true)
        
        // Extract key parameters for inline display
        val keyParams = extractKeyToolParams(toolName, input)
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Tool header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tool icon
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(getToolColor(toolName).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    SimpleText(
                        text = getToolIcon(toolName),
                        color = getToolColor(toolName),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Tool name with key parameters inline
                SimpleText(
                    text = if (keyParams.isNotEmpty()) "$toolName ($keyParams)" else toolName,
                    color = textColor.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Tool content with 3-line preview or expanded view
            if (input != null) {
                when {
                    isToDoWrite -> {
                        // TodoWrite always shows full content with special rendering
                        TodoWriteContent(
                            input = input,
                            textColor = textColor,
                            primaryColor = primaryColor,
                            errorColor = textColor
                        )
                    }
                    toolName.contains("Bash", ignoreCase = true) -> {
                        // For Bash, show description as subtitle
                        val description = extractParameter(input.toString(), "description")
                        if (description.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp, top = 2.dp)
                            ) {
                                SimpleText(
                                    text = description,
                                    color = textColor.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    toolName.contains("Edit", ignoreCase = true) && !toolName.contains("MultiEdit", ignoreCase = true) -> {
                        // For Edit tool, show diff-like display
                        EditToolContent(
                            input = input,
                            textColor = textColor,
                            primaryColor = primaryColor,
                            codeBlockBg = codeBlockBg,
                            isExpanded = isExpanded,
                            onToggleExpanded = { isExpanded = !isExpanded }
                        )
                    }
                    toolName.contains("Write", ignoreCase = true) || 
                    toolName.contains("MultiEdit", ignoreCase = true) -> {
                        // For Write tools, show content preview without border
                        val content = when (input) {
                            is JsonElement -> {
                                try {
                                    input.jsonObject["content"]?.jsonPrimitive?.content ?: ""
                                } catch (e: Exception) {
                                    extractParameter(input.toString(), "content")
                                }
                            }
                            else -> extractParameter(input.toString(), "content")
                        }
                        if (content.isNotEmpty()) {
                            val lines = content.lines()
                            val shouldShowToggle = lines.size > 3
                            val displayText = if (!isExpanded && shouldShowToggle) {
                                lines.take(3).joinToString("\n")
                            } else {
                                content
                            }
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp, top = 4.dp)
                            ) {
                                SimpleText(
                                    text = displayText,
                                    color = textColor.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                
                                // Show more/less link
                                if (shouldShowToggle) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clickable { isExpanded = !isExpanded }
                                    ) {
                                        SimpleText(
                                            text = if (isExpanded) "show less" else "show more",
                                            color = primaryColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // For other tools, show formatted input with border
                        // Skip display for LS and Read tools as path is already in the title
                        if (!toolName.contains("LS", ignoreCase = true) && 
                            !toolName.contains("Read", ignoreCase = true)) {
                            val contentText = formatToolInput(input)
                            val lines = contentText.lines()
                            val shouldShowToggle = lines.size > 3 || contentText.length > 200
                            val displayText = if (!isExpanded && shouldShowToggle) {
                                lines.take(3).joinToString("\n")
                            } else {
                                contentText
                            }
                            
                            if (contentText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 28.dp, top = 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(codeBlockBg)
                                        .border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        SimpleText(
                                            text = displayText,
                                            color = textColor.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        
                                        // Show more/less link
                                        if (shouldShowToggle) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clickable { isExpanded = !isExpanded }
                                            ) {
                                                SimpleText(
                                                    text = if (isExpanded) "show less" else "show more",
                                                    color = primaryColor,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ToolResultCard(
        content: String,
        isError: Boolean,
        textColor: Color,
        errorColor: Color,
        primaryColor: Color,
        codeBlockBg: Color,
        isNested: Boolean = false
    ) {
        var expanded by remember { mutableStateOf(false) }
        val lines = content.lines()
        val shouldShowToggle = lines.size > 3
        val displayText = if (!expanded && shouldShowToggle) {
            lines.take(3).joinToString("\n")
        } else {
            content
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isNested) 28.dp else 0.dp, top = 4.dp)
        ) {
            // Error indicator if applicable
            if (isError) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    SimpleText(
                        text = "❌",
                        color = errorColor,
                        fontSize = 12.sp
                    )
                    SimpleText(
                        text = "Error",
                        color = errorColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Result content without border
            SimpleText(
                text = displayText,
                color = if (isError) errorColor.copy(alpha = 0.9f) else textColor.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            
            // Show more/less link
            if (shouldShowToggle) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                ) {
                    SimpleText(
                        text = if (expanded) "show less" else "show more",
                        color = primaryColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
    
    @Composable
    private fun InputArea(
        enabled: Boolean,
        isLoading: Boolean,
        inputTextValue: TextFieldValue,
        onInputChange: (TextFieldValue) -> Unit,
        completionManager: com.claudecodechat.completion.CompletionManager,
        completionState: com.claudecodechat.completion.CompletionState,
        onSendMessage: (String, String) -> Unit,
        onStop: () -> Unit,
        textColor: Color,
        primaryColor: Color,
        backgroundColor: Color,
        inputBgColor: Color,
        borderColor: Color,
        isDarkTheme: Boolean
    ) {
        var selectedModel by remember { mutableStateOf("auto") }
        var modelDropdownExpanded by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        
        // Get current editor file context
        var currentFile by remember { mutableStateOf<String?>(null) }
        var selectedLines by remember { mutableStateOf<String?>(null) }
        
        // Set up editor listener
        DisposableEffect(Unit) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            
            // Initial file
            currentFile = fileEditorManager.selectedEditor?.file?.presentableName
            
            // Check for selected lines
            val updateSelection = {
                val textEditor = fileEditorManager.selectedTextEditor
                selectedLines = if (textEditor != null && textEditor.selectionModel.hasSelection()) {
                    val startLine = textEditor.document.getLineNumber(textEditor.selectionModel.selectionStart) + 1
                    val endLine = textEditor.document.getLineNumber(textEditor.selectionModel.selectionEnd) + 1
                    if (startLine == endLine) {
                        "Line $startLine selected"
                    } else {
                        "Lines $startLine-$endLine selected"
                    }
                } else null
            }
            updateSelection()
            
            // Listen for file editor changes
            val listener = object : com.intellij.openapi.fileEditor.FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
                    currentFile = file.presentableName
                    updateSelection()
                }
                
                override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                    currentFile = event.newFile?.presentableName
                    updateSelection()
                }
            }
            
            val connection = project.messageBus.connect()
            connection.subscribe(
                com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER,
                listener
            )
            
            // Poll for selection changes
            val timer = javax.swing.Timer(500) {
                updateSelection()
            }
            timer.start()
            
            onDispose {
                timer.stop()
                connection.dispose()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Multi-line input field with keyboard handling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        handleKeyEvent(keyEvent, completionState, completionManager, inputTextValue, onInputChange)
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(inputBgColor)
                    .border(
                        width = 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                    if (inputTextValue.text.isEmpty()) {
                        SimpleText(
                            text = "Message Claude...",
                            color = textColor.copy(alpha = 0.4f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = inputTextValue,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(primaryColor),
                        enabled = enabled
                    )
                }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Bottom row with buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Add button and file context
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add attachment button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDarkTheme) textColor.copy(alpha = 0.05f) else Color(0xFFF5F5F5))
                            .border(
                                width = 1.dp,
                                color = textColor.copy(alpha = if (isDarkTheme) 0.1f else 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = false) { },
                        contentAlignment = Alignment.Center
                    ) {
                        SimpleText(
                            text = "+",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                    
                    // Current file context display
                    if (currentFile != null) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryColor.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = primaryColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SimpleText(
                                    text = "F",
                                    color = primaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                SimpleText(
                                    text = currentFile ?: "",
                                    color = primaryColor.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    
                    // Selected lines indicator
                    if (selectedLines != null) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryColor.copy(alpha = 0.05f))
                                .border(
                                    width = 1.dp,
                                    color = primaryColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SimpleText(
                                    text = "▣",
                                    color = primaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                SimpleText(
                                    text = selectedLines ?: "",
                                    color = primaryColor.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                
                // Right side: Model selector and Send button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Model selector moved to right side
                    Box {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDarkTheme) textColor.copy(alpha = 0.05f) else Color(0xFFF5F5F5))
                                .border(
                                    width = 1.dp,
                                    color = textColor.copy(alpha = if (isDarkTheme) 0.1f else 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { modelDropdownExpanded = !modelDropdownExpanded }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SimpleText(
                                    text = when(selectedModel) {
                                        "auto" -> "Auto"
                                        "opus" -> "Opus"
                                        "sonnet" -> "Sonnet"
                                        else -> selectedModel
                                    },
                                    color = textColor.copy(alpha = 0.8f),
                                    fontSize = 13.sp
                                )
                                SimpleText(
                                    text = "▼",
                                    color = textColor.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        
                        if (modelDropdownExpanded) {
                            Popup(
                                alignment = Alignment.BottomStart,
                                offset = IntOffset(0, -8),
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDarkTheme) Color(0xFF2B2D30) else Color.White)
                                        .border(
                                            width = 1.dp,
                                            color = textColor.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    listOf(
                                        "auto" to "Auto",
                                        "opus" to "Opus", 
                                        "sonnet" to "Sonnet"
                                    ).forEach { (value, label) ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (selectedModel == value)
                                                        primaryColor.copy(alpha = 0.1f)
                                                    else
                                                        Color.Transparent
                                                )
                                                .clickable {
                                                    selectedModel = value
                                                    modelDropdownExpanded = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Column {
                                                SimpleText(
                                                    text = label,
                                                    color = textColor,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (selectedModel == value) FontWeight.Medium else FontWeight.Normal
                                                )
                                                SimpleText(
                                                    text = when(value) {
                                                        "auto" -> "Let Claude choose the best model"
                                                        "opus" -> "Most powerful, best for analysis"
                                                        "sonnet" -> "Balanced performance and speed"
                                                        else -> ""
                                                    },
                                                    color = textColor.copy(alpha = 0.5f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (value != "sonnet") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(textColor.copy(alpha = 0.1f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Send/Stop button with IntelliJ UI style
                    if (isLoading) {
                        // Stop button with red theme
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFDB5860).copy(alpha = 0.1f))
                                .border(1.dp, Color(0xFFDB5860).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clickable { onStop() }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SimpleText(
                                    text = "■",  // Stop icon
                                    color = Color(0xFFDB5860),
                                    fontSize = 10.sp
                                )
                                SimpleText(
                                    text = "Stop",
                                    color = Color(0xFFDB5860),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // Send button
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (inputTextValue.text.isNotBlank()) primaryColor
                                    else if (isDarkTheme) Color(0xFF45494A) else Color(0xFFDFE1E5)
                                )
                                .then(
                                    if (!isDarkTheme && inputTextValue.text.isNotBlank()) 
                                        Modifier.border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    else Modifier
                                )
                                .clickable(enabled = inputTextValue.text.isNotBlank()) {
                                    if (inputTextValue.text.isNotBlank()) {
                                        val modelToUse = if (selectedModel == "auto") "" else selectedModel
                                        onSendMessage(inputTextValue.text, modelToUse)
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                SimpleText(
                                    text = "Send",
                                    color = if (inputTextValue.text.isNotBlank()) Color.White
                                    else if (isDarkTheme) Color(0xFF868A91) else Color(0xFF6F737A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                SimpleText(
                                    text = "→",
                                    color = if (inputTextValue.text.isNotBlank()) Color.White.copy(alpha = 0.9f)
                                    else if (isDarkTheme) Color(0xFF868A91) else Color(0xFF6F737A),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ErrorCard(
        error: com.claudecodechat.models.ErrorInfo,
        textColor: Color,
        errorColor: Color
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(errorColor.copy(alpha = 0.2f))
                .padding(8.dp)
        ) {
            Column {
                SimpleText(
                    text = "Error: ${error.type}",
                    color = errorColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                SimpleText(
                    text = error.message,
                    color = textColor,
                    fontSize = 13.sp
                )
                error.code?.let {
                    SimpleText(
                        text = "Code: $it",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
    
    @Composable
    private fun ClaudeIcon(
        modifier: Modifier = Modifier,
        color: Color = Color(0xFFD97757),
        alpha: Float = 1f
    ) {
        Canvas(modifier = modifier) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2
            
            // Draw a simplified version of Claude logo
            // This creates a star-like pattern similar to the SVG
            val path = Path().apply {
                val points = 12
                val outerRadius = radius * 0.9f
                val innerRadius = radius * 0.4f
                
                for (i in 0 until points) {
                    val angle = (i * 30f - 90f) * (Math.PI / 180f).toFloat()
                    val r = if (i % 2 == 0) outerRadius else innerRadius
                    val x = centerX + r * kotlin.math.cos(angle)
                    val y = centerY + r * kotlin.math.sin(angle)
                    
                    if (i == 0) {
                        moveTo(x, y)
                    } else {
                        lineTo(x, y)
                    }
                }
                close()
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = alpha)
            )
        }
    }
    
    @Composable
    private fun SimpleText(
        text: String,
        color: Color,
        fontSize: androidx.compose.ui.unit.TextUnit,
        fontWeight: FontWeight? = null,
        fontFamily: FontFamily? = null,
        maxLines: Int = Int.MAX_VALUE
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily
            ),
            maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
    
    @Composable
    private fun IntelliJButton(
        text: String,
        onClick: () -> Unit,
        enabled: Boolean = true,
        textColor: Color,
        backgroundColor: Color,
        isDefault: Boolean = false,
        hasDropdown: Boolean = false
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        
        val borderColor = when {
            !enabled -> textColor.copy(alpha = 0.1f)
            isDefault -> Color(0xFF4A9EFF)
            isHovered -> textColor.copy(alpha = 0.3f)
            else -> textColor.copy(alpha = 0.15f)
        }
        
        val bgColor = when {
            !enabled -> backgroundColor
            isDefault && isHovered -> Color(0xFF4A9EFF).copy(alpha = 0.15f)
            isDefault -> Color(0xFF4A9EFF).copy(alpha = 0.08f)
            isHovered -> textColor.copy(alpha = 0.05f)
            else -> backgroundColor
        }
        
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(4.dp)
                )
                .hoverable(interactionSource)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SimpleText(
                    text = text,
                    color = when {
                        !enabled -> textColor.copy(alpha = 0.4f)
                        isDefault -> Color(0xFF4A9EFF)
                        else -> textColor
                    },
                    fontSize = 13.sp,
                    fontWeight = if (isDefault) FontWeight.Medium else FontWeight.Normal
                )
                if (hasDropdown) {
                    SimpleText(
                        text = "▼",
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
    
    private fun getToolIcon(toolName: String): String {
        return when {
            toolName.contains("Read", ignoreCase = true) -> "R"
            toolName.contains("Write", ignoreCase = true) -> "W"
            toolName.contains("Edit", ignoreCase = true) -> "E"
            toolName.contains("MultiEdit", ignoreCase = true) -> "M"
            toolName.contains("Search", ignoreCase = true) -> "S"
            toolName.contains("Grep", ignoreCase = true) -> "G"
            toolName.contains("Bash", ignoreCase = true) -> ">"
            toolName.contains("Task", ignoreCase = true) -> "T"
            toolName.contains("Todo", ignoreCase = true) -> "✓"
            toolName.contains("Web", ignoreCase = true) -> "@"
            toolName.contains("Glob", ignoreCase = true) -> "*"
            toolName.contains("LS", ignoreCase = true) -> "L"
            else -> "•"
        }
    }
    
    private fun getToolColor(toolName: String): Color {
        return when {
            toolName.contains("Read", ignoreCase = true) -> Color(0xFF4CAF50)
            toolName.contains("Write", ignoreCase = true) -> Color(0xFFFF9800)
            toolName.contains("Edit", ignoreCase = true) -> Color(0xFFFFC107)
            toolName.contains("Search", ignoreCase = true) -> Color(0xFF2196F3)
            toolName.contains("Bash", ignoreCase = true) -> Color(0xFF9C27B0)
            toolName.contains("Task", ignoreCase = true) -> Color(0xFF00BCD4)
            toolName.contains("Web", ignoreCase = true) -> Color(0xFF3F51B5)
            else -> Color(0xFF607D8B)
        }
    }
    
    private fun extractToolParams(input: Any?): String {
        if (input == null) return ""
        val str = input.toString()
        // Extract key parameters for preview
        val params = mutableListOf<String>()
        
        // Look for common parameter patterns
        if (str.contains("file_path=")) {
            val path = str.substringAfter("file_path=").substringBefore(",").substringBefore(")")
                .trim('"', ' ')
            if (path.isNotEmpty()) {
                // Show only filename
                params.add(path.substringAfterLast("/"))
            }
        }
        if (str.contains("pattern=")) {
            val pattern = str.substringAfter("pattern=").substringBefore(",").substringBefore(")")
                .trim('"', ' ').take(20)
            if (pattern.isNotEmpty()) params.add(pattern)
        }
        if (str.contains("command=")) {
            val cmd = str.substringAfter("command=").substringBefore(",").substringBefore(")")
                .trim('"', ' ').take(30)
            if (cmd.isNotEmpty()) params.add(cmd)
        }
        
        return params.joinToString(" ")
    }
    
    private fun extractKeyToolParams(toolName: String, input: Any?): String {
        if (input == null) return ""
        
        // Try to extract from JSON if input is JsonElement
        val paramValue = when (input) {
            is JsonElement -> {
                try {
                    val obj = input.jsonObject
                    when {
                        toolName.contains("Read", ignoreCase = true) || 
                        toolName.contains("Write", ignoreCase = true) || 
                        toolName.contains("Edit", ignoreCase = true) -> {
                            obj["file_path"]?.jsonPrimitive?.content ?: ""
                        }
                        toolName.contains("Bash", ignoreCase = true) -> {
                            obj["command"]?.jsonPrimitive?.content ?: ""
                        }
                        toolName.contains("Grep", ignoreCase = true) || 
                        toolName.contains("Search", ignoreCase = true) -> {
                            obj["pattern"]?.jsonPrimitive?.content ?: ""
                        }
                        toolName.contains("Glob", ignoreCase = true) -> {
                            obj["pattern"]?.jsonPrimitive?.content ?: ""
                        }
                        toolName.contains("LS", ignoreCase = true) -> {
                            obj["path"]?.jsonPrimitive?.content ?: ""
                        }
                        else -> ""
                    }
                } catch (e: Exception) {
                    // Fall back to string parsing
                    extractParameterFromString(toolName, input.toString())
                }
            }
            else -> extractParameterFromString(toolName, input.toString())
        }
        
        // Format the parameter based on tool type
        return when {
            toolName.contains("Read", ignoreCase = true) || 
            toolName.contains("Write", ignoreCase = true) || 
            toolName.contains("Edit", ignoreCase = true) -> {
                if (paramValue.isNotEmpty()) {
                    val parts = paramValue.split("/")
                    if (parts.size > 3) {
                        ".../" + parts.takeLast(3).joinToString("/")
                    } else {
                        paramValue
                    }
                } else ""
            }
            toolName.contains("Bash", ignoreCase = true) -> {
                if (paramValue.length > 40) paramValue.take(37) + "..." else paramValue
            }
            toolName.contains("Grep", ignoreCase = true) || 
            toolName.contains("Search", ignoreCase = true) -> {
                if (paramValue.isNotEmpty()) {
                    if (paramValue.length > 30) "\"" + paramValue.take(27) + "...\"" else "\"$paramValue\""
                } else ""
            }
            toolName.contains("Glob", ignoreCase = true) -> {
                if (paramValue.isNotEmpty()) "\"$paramValue\"" else ""
            }
            toolName.contains("LS", ignoreCase = true) -> {
                if (paramValue.isNotEmpty()) {
                    val parts = paramValue.split("/")
                    if (parts.size > 3) {
                        ".../" + parts.takeLast(3).joinToString("/")
                    } else {
                        paramValue
                    }
                } else ""
            }
            toolName.contains("TodoWrite", ignoreCase = true) -> ""
            else -> ""
        }
    }
    
    private fun extractParameterFromString(toolName: String, str: String): String {
        return when {
            toolName.contains("Read", ignoreCase = true) || 
            toolName.contains("Write", ignoreCase = true) || 
            toolName.contains("Edit", ignoreCase = true) -> {
                extractParameter(str, "file_path")
            }
            toolName.contains("Bash", ignoreCase = true) -> {
                extractParameter(str, "command")
            }
            toolName.contains("Grep", ignoreCase = true) || 
            toolName.contains("Search", ignoreCase = true) -> {
                extractParameter(str, "pattern")
            }
            toolName.contains("Glob", ignoreCase = true) -> {
                extractParameter(str, "pattern")
            }
            toolName.contains("LS", ignoreCase = true) -> {
                extractParameter(str, "path")
            }
            else -> ""
        }
    }
    
    private fun formatToolInput(input: Any?): String {
        if (input == null) return "No parameters"
        val str = input.toString()
        
        // Special formatting for Edit tool
        if (str.contains("old_string=") && str.contains("new_string=")) {
            return formatEditToolInput(str)
        }
        
        // Format as key-value pairs for other tools
        return str.replace(", ", "\n")
            .replace("=", ": ")
            .replace("{", "")
            .replace("}", "")
    }
    
    private fun formatEditToolInput(input: String): String {
        val oldStringMatch = "old_string=(.+?), new_string=".toRegex().find(input)
        val newStringMatch = "new_string=(.+?)(?:, \\w+=|$)".toRegex().find(input)
        
        val oldString = oldStringMatch?.groupValues?.get(1) ?: ""
        val newString = newStringMatch?.groupValues?.get(1) ?: ""
        
        fun formatStringPreview(str: String, label: String): String {
            val lines = str.lines()
            return if (lines.size <= 3) {
                "$label:\n$str"
            } else {
                "$label (showing 3 lines):\n${lines.take(3).joinToString("\n")}\n... (${lines.size - 3} more lines)"
            }
        }
        
        return formatStringPreview(oldString, "Old") + "\n\n" + formatStringPreview(newString, "New")
    }
    
    @Composable
    private fun MarkdownText(
        text: String,
        textColor: Color,
        primaryColor: Color,
        codeBlockBg: Color
    ) {
        val elements = parseMarkdown(text)
        
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            elements.forEach { element ->
                when (element.type) {
                    MarkdownElementType.CODE_BLOCK -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(codeBlockBg)
                                .border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                element.language?.let { lang ->
                                    SimpleText(
                                        text = lang,
                                        color = primaryColor.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                SimpleText(
                                    text = element.content,
                                    color = textColor.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    MarkdownElementType.CODE_SPAN -> {
                        Box(
                            modifier = Modifier
                                .background(
                                    codeBlockBg,
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            SimpleText(
                                text = element.content,
                                color = primaryColor,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    MarkdownElementType.HEADING -> {
                        SimpleText(
                            text = element.content,
                            color = textColor,
                            fontSize = when (element.level) {
                                1 -> 18.sp
                                2 -> 16.sp
                                3 -> 15.sp
                                4 -> 14.sp
                                else -> 13.sp
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MarkdownElementType.STRONG -> {
                        SimpleText(
                            text = element.content,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MarkdownElementType.EMPHASIS -> {
                        SimpleText(
                            text = element.content,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    MarkdownElementType.PARAGRAPH, MarkdownElementType.TEXT -> {
                        SimpleText(
                            text = element.content,
                            color = textColor,
                            fontSize = 14.sp
                        )
                    }
                    else -> {
                        // Handle other types like LIST_ITEM if needed
                        SimpleText(
                            text = element.content,
                            color = textColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
    
    // Flexmark parser configuration
    private val flexmarkOptions = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create(),
            TaskListExtension.create()
        ))
    }
    
    private val parser = Parser.builder(flexmarkOptions).build()
    
    data class MarkdownElement(
        val type: MarkdownElementType,
        val content: String,
        val level: Int = 0,
        val language: String? = null
    )
    
    enum class MarkdownElementType {
        TEXT, HEADING, PARAGRAPH, CODE_BLOCK, CODE_SPAN, STRONG, EMPHASIS, LIST_ITEM
    }
    
    private fun parseMarkdown(text: String): List<MarkdownElement> {
        val document = parser.parse(text)
        val elements = mutableListOf<MarkdownElement>()
        
        val visitor = NodeVisitor(
            VisitHandler(Heading::class.java) { node ->
                elements.add(
                    MarkdownElement(
                        type = MarkdownElementType.HEADING,
                        content = node.text.toString(),
                        level = node.level
                    )
                )
            },
            VisitHandler(Paragraph::class.java) { node ->
                processInlineElements(node, elements)
            },
            VisitHandler(FencedCodeBlock::class.java) { node ->
                elements.add(
                    MarkdownElement(
                        type = MarkdownElementType.CODE_BLOCK,
                        content = node.contentChars.toString(),
                        language = node.info.toString().takeIf { it.isNotEmpty() }
                    )
                )
            },
            VisitHandler(IndentedCodeBlock::class.java) { node ->
                elements.add(
                    MarkdownElement(
                        type = MarkdownElementType.CODE_BLOCK,
                        content = node.contentChars.toString()
                    )
                )
            },
            VisitHandler(Text::class.java) { node ->
                if (elements.none { it == elements.lastOrNull() && it.type == MarkdownElementType.TEXT }) {
                    elements.add(
                        MarkdownElement(
                            type = MarkdownElementType.TEXT,
                            content = node.chars.toString()
                        )
                    )
                }
            }
        )
        
        visitor.visitChildren(document)
        return elements
    }
    
    private fun processInlineElements(parent: Node, elements: MutableList<MarkdownElement>) {
        val text = StringBuilder()
        var hasInlineFormatting = false
        
        parent.children.forEach { child ->
            when (child) {
                is Text -> text.append(child.chars)
                is Code -> {
                    if (text.isNotEmpty()) {
                        elements.add(MarkdownElement(MarkdownElementType.TEXT, text.toString()))
                        text.clear()
                    }
                    elements.add(MarkdownElement(MarkdownElementType.CODE_SPAN, child.text.toString()))
                    hasInlineFormatting = true
                }
                is StrongEmphasis -> {
                    if (text.isNotEmpty()) {
                        elements.add(MarkdownElement(MarkdownElementType.TEXT, text.toString()))
                        text.clear()
                    }
                    elements.add(MarkdownElement(MarkdownElementType.STRONG, child.text.toString()))
                    hasInlineFormatting = true
                }
                is Emphasis -> {
                    if (text.isNotEmpty()) {
                        elements.add(MarkdownElement(MarkdownElementType.TEXT, text.toString()))
                        text.clear()
                    }
                    elements.add(MarkdownElement(MarkdownElementType.EMPHASIS, child.text.toString()))
                    hasInlineFormatting = true
                }
                else -> text.append(child.chars)
            }
        }
        
        if (text.isNotEmpty()) {
            elements.add(
                MarkdownElement(
                    type = if (hasInlineFormatting) MarkdownElementType.TEXT else MarkdownElementType.PARAGRAPH,
                    content = text.toString()
                )
            )
        }
    }
    
    
    /**
     * Handle keyboard events for completion navigation
     */
    private fun handleKeyEvent(
        keyEvent: KeyEvent,
        completionState: com.claudecodechat.completion.CompletionState,
        completionManager: com.claudecodechat.completion.CompletionManager,
        currentTextValue: TextFieldValue,
        onTextValueChange: (TextFieldValue) -> Unit
    ): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        if (!completionState.isShowing) return false
        
        return when (keyEvent.key) {
            Key.DirectionUp -> {
                completionManager.selectPrevious()
                true
            }
            Key.DirectionDown -> {
                completionManager.selectNext()
                true
            }
            Key.Tab, Key.Enter -> {
                val result = completionManager.acceptCompletion(
                    currentTextValue.text,
                    currentTextValue.selection.end
                )
                if (result != null) {
                    onTextValueChange(TextFieldValue(
                        text = result.newText,
                        selection = TextRange(result.newCursorPosition)
                    ))
                }
                true
            }
            Key.Escape -> {
                completionManager.hideCompletion()
                true
            }
            else -> false
        }
    }
}