package top.ichiki.whitenoise.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import top.ichiki.whitenoise.data.SceneConfig
import top.ichiki.whitenoise.ui.PlayerViewModel

/**
 * 新建场景对话框：选图片(跳转裁切界面) + 输入标题(有默认值) + 多选音频(实时播放预览)
 */
@Composable
fun CreateSceneDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var croppedScale by remember { mutableFloatStateOf(1f) }
    var croppedOffset by remember { mutableStateOf(Offset.Zero) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selectedAudio = remember { mutableStateListOf<String>() }

    // 是否处于裁切模式
    var isCropping by remember { mutableStateOf(false) }
    // 裁切中的临时 uri
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
            isCropping = true
        }
    }

    val customSounds by viewModel.customSounds.collectAsState()
    val sounds = remember(customSounds) { viewModel.allSounds() }

    // 打开对话框时暂停当前播放，关闭时恢复
    val wasPlaying = remember { viewModel.isPlaying.value }
    val sceneToRestore = remember { viewModel.currentScene.value }
    DisposableEffect(Unit) {
        if (wasPlaying) viewModel.pause()
        onDispose {
            viewModel.stopPreviewAudio()
            if (wasPlaying && sceneToRestore != null) {
                viewModel.replayCurrentScene()
            }
        }
    }

    // 当音频选择变化时实时播放预览
    LaunchedEffect(selectedAudio.toList()) {
        viewModel.previewAudioSelection(selectedAudio.toList())
    }

    val defaultTitle = "我的场景"

    Dialog(
        onDismissRequest = { if (!isCropping) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (isCropping && pendingCropUri != null) {
            // ===== 独立裁切界面 =====
            ImageCropView(
                imageUri = pendingCropUri!!,
                onConfirm = { scale, offset ->
                    imageUri = pendingCropUri
                    croppedScale = scale
                    croppedOffset = offset
                    isCropping = false
                },
                onCancel = { isCropping = false }
            )
        } else {
            // ===== 主创建界面 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                color = Color(0xFF1E1E3A),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("新建场景", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "关闭", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            // 图片缩略图区域（小尺寸，不占太多空间）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 128.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0D0D1A))
                                        .clickable { pickImage.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(imageUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "场景封面",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = croppedScale,
                                                    scaleY = croppedScale,
                                                    translationX = croppedOffset.x * 0.3f,
                                                    translationY = croppedOffset.y * 0.3f
                                                )
                                        )
                                    } else {
                                        Text(
                                            "+",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 24.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (imageUri != null) "点击重新选择" else "选择封面图片（可选）",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "不选则使用默认深色背景",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            SimpleField("场景名称（默认：$defaultTitle）", title, onChange = { title = it })
                            Spacer(Modifier.height(8.dp))
                            SimpleField("描述（可选）", description, onChange = { description = it })
                            Spacer(Modifier.height(12.dp))
                            Text("选择音频组合", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(sounds, key = { it.id }) { sound ->
                            val isSelected = selectedAudio.contains(sound.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) {
                                        if (isSelected) selectedAudio.remove(sound.id) else selectedAudio.add(sound.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    sound.title + if (sound.isCustom) " (自定义)" else "",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (selectedAudio.isNotEmpty()) {
                                val finalTitle = title.ifBlank { defaultTitle }
                                viewModel.addCustomScene(imageUri, finalTitle, description, selectedAudio.toList(), croppedScale, croppedOffset.x, croppedOffset.y)
                                onDismiss()
                            }
                        },
                        enabled = selectedAudio.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text("创建", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * 独立的图片裁切界面（全屏接管 Dialog 内容）
 * 9:16 比例预览框，双指缩放拖动
 * offset 被限制在图片能覆盖整个预览框的范围内，不会出现黑色背景
 */
@Composable
private fun ImageCropView(
    imageUri: Uri,
    onConfirm: (scale: Float, offset: Offset) -> Unit,
    onCancel: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF111111)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 顶部栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text("取消", color = Color.White.copy(alpha = 0.7f))
                }
                Text("调整显示区域", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                TextButton(onClick = { onConfirm(scale, offset) }) {
                    Text("确定", color = Color(0xFF8B5CF6))
                }
            }

            Spacer(Modifier.height(16.dp))

            // 裁切预览区域 9:16
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(8.dp))
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                // 计算允许的最大偏移：图片放大后超出预览框的部分
                                val maxX = (newScale - 1f) * boxSize.width / 2f
                                val maxY = (newScale - 1f) * boxSize.height / 2f
                                val newOffset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                                scale = newScale
                                offset = newOffset
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "裁切预览",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "双指缩放和拖动来调整显示区域",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SimpleField(
    placeholder: String,
    value: String,
    onChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp)
        )
    }
}

/**
 * 编辑已有自定义场景的对话框
 */
@Composable
fun EditSceneDialog(
    viewModel: PlayerViewModel,
    scene: SceneConfig,
    onDismiss: () -> Unit
) {
    val customScenes by viewModel.customScenes.collectAsState()
    val customScene = remember(scene.title, customScenes) {
        customScenes.find { it.title == scene.title }
    } ?: run { onDismiss(); return }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var croppedScale by remember { mutableFloatStateOf(customScene.cropScale) }
    var croppedOffset by remember { mutableStateOf(Offset(customScene.cropOffsetX, customScene.cropOffsetY)) }
    var title by remember { mutableStateOf(customScene.title) }
    var description by remember { mutableStateOf(customScene.description) }
    val selectedAudio = remember { mutableStateListOf(*customScene.audioIds.toTypedArray()) }

    var isCropping by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
            isCropping = true
        }
    }

    val customSounds by viewModel.customSounds.collectAsState()
    val sounds = remember(customSounds) { viewModel.allSounds() }

    val wasPlaying = remember { viewModel.isPlaying.value }
    val sceneToRestore = remember { viewModel.currentScene.value }
    DisposableEffect(Unit) {
        if (wasPlaying) viewModel.pause()
        onDispose {
            viewModel.stopPreviewAudio()
            if (wasPlaying && sceneToRestore != null) {
                viewModel.replayCurrentScene()
            }
        }
    }

    LaunchedEffect(selectedAudio.toList()) {
        viewModel.previewAudioSelection(selectedAudio.toList())
    }

    Dialog(
        onDismissRequest = { if (!isCropping) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (isCropping && pendingCropUri != null) {
            ImageCropView(
                imageUri = pendingCropUri!!,
                onConfirm = { scale, offset ->
                    imageUri = pendingCropUri
                    croppedScale = scale
                    croppedOffset = offset
                    isCropping = false
                },
                onCancel = { isCropping = false }
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                color = Color(0xFF1E1E3A),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("编辑场景", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "关闭", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 128.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0D0D1A))
                                        .clickable { pickImage.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (imageUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(imageUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "场景封面",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = croppedScale,
                                                    scaleY = croppedScale,
                                                    translationX = croppedOffset.x * 0.3f,
                                                    translationY = croppedOffset.y * 0.3f
                                                )
                                        )
                                    } else if (customScene.imagePath.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(java.io.File(customScene.imagePath))
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "场景封面",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = croppedScale,
                                                    scaleY = croppedScale,
                                                    translationX = croppedOffset.x * 0.3f,
                                                    translationY = croppedOffset.y * 0.3f
                                                )
                                        )
                                    } else {
                                        Text(
                                            "+",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 24.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "点击更换封面",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "不更换则保留原图",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            SimpleField("场景名称", title, onChange = { title = it })
                            Spacer(Modifier.height(8.dp))
                            SimpleField("描述（可选）", description, onChange = { description = it })
                            Spacer(Modifier.height(12.dp))
                            Text("选择音频组合", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(sounds, key = { it.id }) { sound ->
                            val isSelected = selectedAudio.contains(sound.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) {
                                        if (isSelected) selectedAudio.remove(sound.id) else selectedAudio.add(sound.id)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    sound.title + if (sound.isCustom) " (自定义)" else "",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (selectedAudio.isNotEmpty()) {
                                val finalTitle = title.ifBlank { "我的场景" }
                                viewModel.updateCustomScene(
                                    originalTitle = customScene.title,
                                    imageUri = imageUri,
                                    newTitle = finalTitle,
                                    description = description,
                                    audioIds = selectedAudio.toList(),
                                    cropScale = croppedScale,
                                    cropOffsetX = croppedOffset.x,
                                    cropOffsetY = croppedOffset.y
                                )
                                onDismiss()
                            }
                        },
                        enabled = selectedAudio.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Text("保存", color = Color.White)
                    }
                }
            }
        }
    }
}
