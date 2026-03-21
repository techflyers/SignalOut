package com.signalout.android.ui
// [Goose] TODO: Replace inline file attachment stub with FilePickerButton abstraction that dispatches via FileShareDispatcher


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.signalout.android.R
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.withStyle
import com.signalout.android.ui.theme.WhatsAppColors
import com.signalout.android.features.voice.normalizeAmplitudeSample
import com.signalout.android.features.voice.AudioWaveformExtractor
import com.signalout.android.ui.media.RealtimeScrollingWaveform
import com.signalout.android.ui.media.ImagePickerButton
import com.signalout.android.ui.media.FilePickerButton

/**
 * Input components for ChatScreen - WhatsApp-style design
 */

/**
 * VisualTransformation that styles slash commands with background and color
 */
class SlashCommandVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val slashCommandRegex = Regex("(/\\w+)(?=\\s|$)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            slashCommandRegex.findAll(text.text).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }
                withStyle(
                    style = SpanStyle(
                        color = WhatsAppColors.TealAccent,
                        fontWeight = FontWeight.Medium,
                        background = WhatsAppColors.DarkSurfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }
        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * VisualTransformation that styles mentions
 */
class MentionVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val mentionRegex = Regex("@([a-zA-Z0-9_]+)")
        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            mentionRegex.findAll(text.text).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(text.text.substring(lastIndex, match.range.first))
                }
                withStyle(
                    style = SpanStyle(
                        color = WhatsAppColors.BlueTick,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.text.length) {
                append(text.text.substring(lastIndex))
            }
        }
        return TransformedText(
            text = annotatedString,
            offsetMapping = OffsetMapping.Identity
        )
    }
}

/**
 * Combined visual transformation
 */
class CombinedVisualTransformation(private val transformations: List<VisualTransformation>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        var resultText = text
        transformations.forEach { transformation ->
            resultText = transformation.filter(resultText).text
        }
        return TransformedText(
            text = resultText,
            offsetMapping = OffsetMapping.Identity
        )
    }
}


/**
 * WhatsApp-style message input bar
 */
@Composable
fun MessageInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    showMediaButtons: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == WhatsAppColors.DarkBackground
    val isFocused = remember { mutableStateOf(false) }
    val hasText = value.text.isNotBlank()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var amplitude by remember { mutableStateOf(0) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    if (showEmojiPicker) {
        EmojiPickerBottomSheet(
            onEmojiSelected = { emoji ->
                val text = value.text
                val selection = value.selection
                val newText = text.substring(0, selection.start) + emoji + text.substring(selection.end)
                val newSelection = selection.start + emoji.length
                onValueChange(TextFieldValue(newText, androidx.compose.ui.text.TextRange(newSelection)))
            },
            onDismissRequest = { showEmojiPicker = false }
        )
    }

    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Main input field container (rounded pill shape - WhatsApp style)
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) WhatsAppColors.DarkSurface else Color.White,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Emoji button (left side)
                Icon(
                    imageVector = Icons.Filled.EmojiEmotions,
                    contentDescription = "Emoji",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showEmojiPicker = true },
                    tint = colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(8.dp))

                // Text input area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .padding(vertical = 8.dp)
                ) {
                    if (isRecording) {
                        // Waveform overlay when recording
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RealtimeScrollingWaveform(
                                modifier = Modifier.weight(1f).height(32.dp),
                                amplitudeNorm = normalizeAmplitudeSample(amplitude)
                            )
                            Spacer(Modifier.width(8.dp))
                            val secs = (elapsedMs / 1000).toInt()
                            val mm = secs / 60
                            val ss = secs % 60
                            Text(
                                text = String.format("%02d:%02d", mm, ss),
                                color = Color.Red,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (hasText) onSend()
                            }),
                            visualTransformation = CombinedVisualTransformation(
                                listOf(SlashCommandVisualTransformation(), MentionVisualTransformation())
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { focusState ->
                                    isFocused.value = focusState.isFocused
                                }
                        )
                        // Placeholder
                        if (value.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.type_a_message_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Attachment & Camera buttons inside the pill (right side) — only when no text
                if (value.text.isEmpty() && showMediaButtons && !isRecording) {
                    val latestSelectedPeer = rememberUpdatedState(selectedPrivatePeer)
                    val latestChannel = rememberUpdatedState(currentChannel)

                    // Attachment icon (paperclip)
                    FilePickerButton(
                        onFileReady = { path ->
                            onSendFileNote(latestSelectedPeer.value, latestChannel.value, path)
                        }
                    )

                    Spacer(Modifier.width(4.dp))

                    // Camera / image picker
                    ImagePickerButton(
                        onImageReady = { outPath ->
                            onSendImageNote(latestSelectedPeer.value, latestChannel.value, outPath)
                        }
                    )
                }
            }
        }

        // Right side action button — either send or voice record
        if (hasText) {
            // Send button (circular teal with white arrow - WhatsApp style)
            IconButton(
                onClick = { onSend() },
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = WhatsAppColors.TealAccent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = stringResource(id = R.string.send_message),
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                }
            }
        } else if (showMediaButtons) {
            // Mic button (circular teal - WhatsApp style)
            val bg = WhatsAppColors.TealAccent
            val latestSelectedPeer = rememberUpdatedState(selectedPrivatePeer)
            val latestChannel = rememberUpdatedState(currentChannel)
            val latestOnSendVoiceNote = rememberUpdatedState(onSendVoiceNote)

            VoiceRecordButton(
                modifier = Modifier.size(48.dp),
                backgroundColor = bg,
                onStart = {
                    isRecording = true
                    elapsedMs = 0L
                    if (isFocused.value) {
                        try { focusRequester.requestFocus() } catch (_: Exception) {}
                    }
                },
                onAmplitude = { amp, ms ->
                    amplitude = amp
                    elapsedMs = ms
                },
                onFinish = { path ->
                    isRecording = false
                    AudioWaveformExtractor.extractAsync(path, sampleCount = 120) { arr ->
                        if (arr != null) {
                            try { com.signalout.android.features.voice.VoiceWaveformCache.put(path, arr) } catch (_: Exception) {}
                        }
                    }
                    latestOnSendVoiceNote.value(
                        latestSelectedPeer.value,
                        latestChannel.value,
                        path
                    )
                }
            )
        } else {
            // Disabled send button when no media buttons
            IconButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.Gray.copy(alpha = 0.3f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = stringResource(id = R.string.send_message),
                        modifier = Modifier.size(22.dp),
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun CommandSuggestionsBox(
    suggestions: List<CommandSuggestion>,
    onSuggestionClick: (CommandSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: CommandSuggestion ->
            CommandSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun CommandSuggestionItem(
    suggestion: CommandSuggestion,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val allCommands = if (suggestion.aliases.isNotEmpty()) {
            listOf(suggestion.command) + suggestion.aliases
        } else {
            listOf(suggestion.command)
        }

        Text(
            text = allCommands.joinToString(", "),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.primary,
            fontSize = 13.sp
        )

        suggestion.syntax?.let { syntax ->
            Text(
                text = syntax,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }

        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MentionSuggestionsBox(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: String ->
            MentionSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun MentionSuggestionItem(
    suggestion: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.mention_suggestion_at, suggestion),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = WhatsAppColors.BlueTick,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.mention),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

val COMMON_EMOJIS = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
    "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
    "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
    "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
    "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾",
    "👍", "👎", "✊", "👊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️",
    "🖖", "👋", "🤙", "💪", "🖕", "✍️", "🙏", "🦶", "🦵", "🦿", "🦾", "🦷", "🦴", "👀", "👁️", "👅", "👄", "💋", "🩸",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerBottomSheet(
    onEmojiSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Emojis",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(COMMON_EMOJIS.size) { index ->
                    Text(
                        text = COMMON_EMOJIS[index],
                        fontSize = 30.sp,
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable {
                                onEmojiSelected(COMMON_EMOJIS[index])
                            },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
