package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.Booker
import com.example.domain.model.BusinessUser
import com.example.domain.model.ChatGroup
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatMessageType
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabWhite
import com.example.util.AudioVoicePlayer
import com.example.util.AudioVoiceRecorder
import com.example.util.ChatMediaHelper
import com.example.util.PakistanTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatGroupsListScreen(
    session: ActiveWorkspaceSession,
    chatGroups: List<ChatGroup>,
    bookers: List<Booker>,
    staffUsers: List<BusinessUser>,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onCreateGroup: (
        name: String,
        description: String,
        memberAccountIds: List<String>,
        memberNames: List<String>,
        onDone: (ChatGroup) -> Unit
    ) -> Unit
) {
    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Team Meeting Groups",
                subtitle = if (isSuperAdmin) "Create groups for Bookers & Staff" else "${chatGroups.size} active meeting groups",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (isSuperAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = HisabNavy,
                    contentColor = HisabWhite,
                    modifier = Modifier.testTag("create_chat_group_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Meeting Group")
                }
            }
        },
        containerColor = HisabLightGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            if (chatGroups.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Default.Forum,
                        title = "No Meeting Groups Yet",
                        message = if (isSuperAdmin) {
                            "Tap + to create a team meeting group and select which Bookers & Staff join."
                        } else {
                            "You have not been added to a meeting group yet. Contact your Super Admin."
                        }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(chatGroups, key = { it.id }) { group ->
                        Card(
                            onClick = { onOpenGroup(group.id) },
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_group_card_${group.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(HisabKpiBlueBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = HisabNavy,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = HisabBlackText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        StatusPill(
                                            text = "${group.memberAccountIds.size} Members",
                                            containerColor = HisabNavyContainer,
                                            contentColor = HisabNavy
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = group.lastMessagePreview.ifBlank { "Tap to open group meeting" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (group.lastMessageAtMillis > 0L) {
                                        Text(
                                            text = "${PakistanTime.formatDate(group.lastMessageAtMillis)} • ${PakistanTime.formatTime(group.lastMessageAtMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray
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

    if (showCreateDialog && isSuperAdmin) {
        GroupCreateOrMembersDialog(
            title = "Create Meeting Group",
            initialName = "",
            initialDescription = "",
            initialSelectedIds = emptySet(),
            allowEditMeta = true,
            bookers = bookers,
            staffUsers = staffUsers,
            isSubmitting = isSubmitting,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc, ids, names ->
                onCreateGroup(name, desc, ids, names) { created ->
                    showCreateDialog = false
                    onOpenGroup(created.id)
                }
            }
        )
    }
}

@Composable
fun GroupChatDetailScreen(
    session: ActiveWorkspaceSession,
    group: ChatGroup?,
    messages: List<ChatMessage>,
    bookers: List<Booker>,
    staffUsers: List<BusinessUser>,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onNotifyAction: (Boolean, String) -> Unit,
    onBack: () -> Unit,
    onUpdateMembers: (ChatGroup, List<String>, List<String>, () -> Unit) -> Unit,
    onSendText: (String, () -> Unit) -> Unit,
    onSendMedia: (
        type: ChatMessageType,
        text: String,
        mediaBase64: String,
        fileName: String,
        mimeType: String,
        durationSec: Int,
        fileSizeBytes: Long
    ) -> Unit
) {
    val context = LocalContext.current
    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN
    val listState = rememberLazyListState()

    var messageText by remember { mutableStateOf("") }
    var showMembersDialog by remember { mutableStateOf(false) }

    // Voice recorder & player
    val voiceRecorder = remember(context) { AudioVoiceRecorder(context.applicationContext) }
    val voicePlayer = remember(context) { AudioVoicePlayer(context.applicationContext) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.stopSilently()
            voicePlayer.stop()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000L)
                recordingSeconds += 1
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Camera capture URI holder
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()
    var isPreparingMedia by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            scope.launch {
                isPreparingMedia = true
                val encodedResult = withContext(Dispatchers.IO) {
                    ChatMediaHelper.encodeImageUriToBase64(context, uri)
                }
                isPreparingMedia = false
                encodedResult
                    .onSuccess { encoded ->
                        onSendMedia(
                            ChatMessageType.IMAGE,
                            messageText.trim(),
                            encoded.base64,
                            encoded.fileName,
                            encoded.mimeType,
                            0,
                            encoded.sizeBytes
                        )
                        messageText = ""
                    }
                    .onFailure { err ->
                        onNotifyAction(true, err.localizedMessage ?: "Failed to process captured photo.")
                    }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = ChatMediaHelper.createCameraImageUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            onNotifyAction(true, "Camera permission is required to capture photos for group chat.")
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording()
                .onSuccess { isRecording = true }
                .onFailure { err ->
                    onNotifyAction(true, err.localizedMessage ?: "Unable to start microphone.")
                }
        } else {
            onNotifyAction(true, "Microphone permission is required to send voice notes.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparingMedia = true
                val encodedResult = withContext(Dispatchers.IO) {
                    ChatMediaHelper.encodeImageUriToBase64(context, uri)
                }
                isPreparingMedia = false
                encodedResult
                    .onSuccess { encoded ->
                        onSendMedia(
                            ChatMessageType.IMAGE,
                            messageText.trim(),
                            encoded.base64,
                            encoded.fileName,
                            encoded.mimeType,
                            0,
                            encoded.sizeBytes
                        )
                        messageText = ""
                    }
                    .onFailure { err ->
                        onNotifyAction(true, err.localizedMessage ?: "Failed to attach image.")
                    }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isPreparingMedia = true
                val encodedResult = withContext(Dispatchers.IO) {
                    ChatMediaHelper.readFileUriToBase64(context, uri)
                }
                isPreparingMedia = false
                encodedResult
                    .onSuccess { encoded ->
                        onSendMedia(
                            ChatMessageType.FILE,
                            messageText.trim(),
                            encoded.base64,
                            encoded.fileName,
                            encoded.mimeType,
                            0,
                            encoded.sizeBytes
                        )
                        messageText = ""
                    }
                    .onFailure { err ->
                        onNotifyAction(true, err.localizedMessage ?: "Could not attach file.")
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = group?.name ?: "Meeting Group",
                subtitle = group?.let { "${it.memberAccountIds.size} members" },
                onBack = onBack,
                actions = {
                    if (isSuperAdmin && group != null) {
                        IconButton(
                            onClick = { showMembersDialog = true },
                            modifier = Modifier.testTag("manage_chat_members_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "Manage Group Members"
                            )
                        }
                    }
                }
            )
        },
        containerColor = HisabLightGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            if (group == null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Default.Groups,
                        title = "Group Unavailable",
                        message = "This meeting group could not be found or you do not have access."
                    )
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Forum,
                            title = "Start Team Meeting",
                            message = "Send a text message, record a voice note, snap a camera photo, or share files with group members."
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = msg.senderAccountId == session.accountId
                        ChatMessageBubble(
                            message = msg,
                            isMine = isMine,
                            isPlayingVoice = playingMsgId == msg.id,
                            onToggleVoice = {
                                voicePlayer.playOrToggle(msg.id, msg.mediaBase64) { activeId ->
                                    playingMsgId = activeId
                                }
                            },
                            onOpenAttachment = {
                                ChatMediaHelper.openChatFile(context, msg.mediaBase64, msg.fileName, msg.mimeType)
                            },
                            onDownloadAttachment = {
                                ChatMediaHelper.saveChatMediaToDownloads(
                                    context, msg.mediaBase64, msg.fileName, msg.mimeType
                                ).onSuccess { path ->
                                    onNotifyAction(false, "Saved to $path")
                                }.onFailure { err ->
                                    onNotifyAction(true, err.localizedMessage ?: "Download failed.")
                                }
                            }
                        )
                    }
                }
            }

            // Bottom Composer
            Surface(
                color = HisabWhite,
                border = BorderStroke(1.dp, HisabBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HisabErrorBg)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = HisabErrorRed)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Recording voice note (${recordingSeconds}s)...",
                                color = HisabErrorRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    isRecording = false
                                    voiceRecorder.stopSilently()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Recording", tint = HisabErrorRed)
                            }
                            Button(
                                onClick = {
                                    isRecording = false
                                    voiceRecorder.stopAndEncode()
                                        .onSuccess { (b64, durationSec, sizeBytes) ->
                                            onSendMedia(
                                                ChatMessageType.VOICE,
                                                "",
                                                b64,
                                                "voice_${System.currentTimeMillis()}.m4a",
                                                "audio/mp4",
                                                durationSec,
                                                sizeBytes
                                            )
                                        }
                                        .onFailure { err ->
                                            onNotifyAction(true, err.localizedMessage ?: "Voice recording failed.")
                                        }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Send Voice")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isPreparingMedia) {
                            LinearProgressIndicator(
                                color = HisabNavy,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val attachmentsEnabled = !isPreparingMedia && !isSubmitting
                            IconButton(
                                enabled = attachmentsEnabled,
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        voiceRecorder.startRecording()
                                            .onSuccess { isRecording = true }
                                            .onFailure { err ->
                                                onNotifyAction(true, err.localizedMessage ?: "Unable to start recording.")
                                            }
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.testTag("chat_voice_button")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Record Voice Note", tint = HisabNavy)
                            }
                            IconButton(
                                enabled = attachmentsEnabled,
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        val uri = ChatMediaHelper.createCameraImageUri(context)
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.testTag("chat_camera_button")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera Photo", tint = HisabNavy)
                            }
                            IconButton(
                                enabled = attachmentsEnabled,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("chat_gallery_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick Photo", tint = HisabNavy)
                            }
                            IconButton(
                                enabled = attachmentsEnabled,
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.testTag("chat_file_button")
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach File", tint = HisabNavy)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = { Text("Type message for team...") },
                                maxLines = 3,
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = HisabLightGray,
                                    unfocusedContainerColor = HisabLightGray,
                                    focusedBorderColor = HisabNavy,
                                    unfocusedBorderColor = HisabBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chat_message_input")
                            )
                            Surface(
                                color = if (messageText.isNotBlank() && !isSubmitting) HisabNavy else HisabBorder,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val textToSend = messageText.trim()
                                        if (textToSend.isNotEmpty()) {
                                            onSendText(textToSend) { messageText = "" }
                                        }
                                    },
                                    enabled = messageText.isNotBlank() && !isSubmitting,
                                    modifier = Modifier.testTag("chat_send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Message",
                                        tint = HisabWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMembersDialog && group != null && isSuperAdmin) {
        GroupCreateOrMembersDialog(
            title = "Manage Group Members",
            initialName = group.name,
            initialDescription = group.description,
            initialSelectedIds = group.memberAccountIds.toSet(),
            allowEditMeta = false,
            bookers = bookers,
            staffUsers = staffUsers,
            isSubmitting = isSubmitting,
            onDismiss = { showMembersDialog = false },
            onConfirm = { _, _, ids, names ->
                onUpdateMembers(group, ids, names) { showMembersDialog = false }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isPlayingVoice: Boolean,
    onToggleVoice: () -> Unit,
    onOpenAttachment: () -> Unit,
    onDownloadAttachment: () -> Unit
) {
    val msgType = ChatMessageType.fromKey(message.messageType)
    val bubbleColor = if (isMine) HisabNavy else HisabWhite
    val primaryTextColor = if (isMine) HisabWhite else HisabBlackText
    val secondaryTextColor = if (isMine) HisabWhite.copy(alpha = 0.78f) else HisabSecondaryGray

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = if (isMine) null else BorderStroke(1.dp, HisabBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold
                    )
                    StatusPill(
                        text = message.senderRole,
                        containerColor = if (isMine) HisabWhite.copy(alpha = 0.18f) else HisabNavyContainer,
                        contentColor = if (isMine) HisabWhite else HisabNavy
                    )
                }

                when (msgType) {
                    ChatMessageType.TEXT -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryTextColor
                        )
                    }

                    ChatMessageType.VOICE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (isMine) HisabWhite else HisabNavy,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                IconButton(onClick = onToggleVoice) {
                                    Icon(
                                        imageVector = if (isPlayingVoice) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingVoice) "Stop Voice Note" else "Play Voice Note",
                                        tint = if (isMine) HisabNavy else HisabWhite
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isPlayingVoice) "Playing voice note..." else "Voice Note",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = primaryTextColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${message.durationSec}s • ${(message.fileSizeBytes / 1024).coerceAtLeast(1)} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor
                                )
                            }
                            IconButton(onClick = onDownloadAttachment) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save Voice Note",
                                    tint = primaryTextColor
                                )
                            }
                        }
                    }

                    ChatMessageType.IMAGE -> {
                        val bmp = remember(message.id, message.mediaBase64) {
                            ChatMediaHelper.decodeBase64ToBitmap(message.mediaBase64)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = message.fileName.ifBlank { "Shared Photo" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpenAttachment() }
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Text(message.text, style = MaterialTheme.typography.bodySmall, color = primaryTextColor)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenAttachment) {
                                Text("Open", color = primaryTextColor)
                            }
                            TextButton(onClick = onDownloadAttachment) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = primaryTextColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save", color = primaryTextColor)
                            }
                        }
                    }

                    ChatMessageType.FILE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = primaryTextColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.fileName.ifBlank { "Shared Document" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = primaryTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${(message.fileSizeBytes / 1024).coerceAtLeast(1)} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenAttachment) {
                                Text("Open File", color = primaryTextColor)
                            }
                            TextButton(onClick = onDownloadAttachment) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = primaryTextColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Download", color = primaryTextColor)
                            }
                        }
                    }
                }

                Text(
                    text = "${message.dateFormatted} • ${message.timeFormatted} PKT",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor
                )
            }
        }
    }
}

private data class SelectableParticipant(
    val accountId: String,
    val fullName: String,
    val subtitle: String,
    val roleLabel: String
)

@Composable
private fun GroupCreateOrMembersDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    initialSelectedIds: Set<String>,
    allowEditMeta: Boolean,
    bookers: List<Booker>,
    staffUsers: List<BusinessUser>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, memberIds: List<String>, memberNames: List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var selectedIds by remember { mutableStateOf(initialSelectedIds) }

    val participants = remember(bookers, staffUsers) {
        buildList {
            staffUsers.filter { it.role == AccountRole.STAFF.key && it.status == "ACTIVE" }.forEach { u ->
                add(SelectableParticipant(u.id, u.fullName, "${u.userCode} • @${u.username}", "Staff"))
            }
            bookers.filter { it.status == "ACTIVE" }.forEach { b ->
                add(SelectableParticipant(b.id, b.fullName, "${b.bookerCode} • @${b.username}", "Booker"))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text(title, color = HisabNavy, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (allowEditMeta) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Group Name *") },
                        placeholder = { Text("Daily Recovery Meeting") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("chat_group_name_input")
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Topic / Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = HisabBorder)
                }

                Text(
                    text = "Select Bookers & Staff Members (${selectedIds.size} selected)",
                    style = MaterialTheme.typography.labelLarge,
                    color = HisabNavy,
                    fontWeight = FontWeight.Bold
                )
                if (participants.isEmpty()) {
                    Text(
                        text = "No active Bookers or Staff users yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )
                } else {
                    participants.forEach { p ->
                        val checked = selectedIds.contains(p.accountId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (checked) selectedIds - p.accountId else selectedIds + p.accountId
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = HisabNavy
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(p.subtitle, style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                            }
                            StatusPill(p.roleLabel, HisabNavyContainer, HisabNavy)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = participants.filter { selectedIds.contains(it.accountId) }
                    onConfirm(
                        name.trim(),
                        description.trim(),
                        chosen.map { it.accountId },
                        chosen.map { it.fullName }
                    )
                },
                enabled = !isSubmitting && (!allowEditMeta || name.trim().length >= 2),
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                modifier = Modifier.testTag("confirm_chat_group_button")
            ) {
                Text(if (allowEditMeta) "Create Group" else "Save Members")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
