package top.ichiki.whitenoise.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.ichiki.whitenoise.data.SoundItem
import top.ichiki.whitenoise.data.SoundRepository
import top.ichiki.whitenoise.ui.PlayerViewModel
import top.ichiki.whitenoise.ui.components.rememberIconBitmap

@Composable
fun CustomScreen(viewModel: PlayerViewModel) {
    val activeSounds by viewModel.activeSounds.collectAsState()
    val customSounds by viewModel.customSounds.collectAsState()
    val builtIn = SoundRepository.sounds
    val customAsItems = remember(customSounds) {
        customSounds.map { c ->
            SoundItem(
                id = c.id,
                title = c.title,
                engTitle = c.title,
                iconFile = "",
                audioFile = c.audioPath,
                colorStart = 0xFF8B5CF6,
                colorEnd = 0xFFEC4899,
                defaultVolume = 1.0f,
                isCustom = true
            )
        }
    }

    val context = LocalContext.current
    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        // 持久化访问授权
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        // 查询显示名
        val name = queryDisplayName(context, uri) ?: "imported.mp3"
        viewModel.importCustomSound(uri, name)
    }

    // 长按删除确认
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 56.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(builtIn.size, key = { builtIn[it].id }) { idx ->
            val sound = builtIn[idx]
            SoundGridItem(
                name = sound.title,
                iconPath = "icons/${sound.iconFile}",
                colorStart = Color(sound.colorStart),
                colorEnd = Color(sound.colorEnd),
                isSelected = activeSounds.contains(sound.id),
                onClick = { viewModel.toggleSound(sound.id) }
            )
        }
        if (customAsItems.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                Text(
                    "我的导入",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(customAsItems.size, key = { customAsItems[it].id }) { idx ->
                val sound = customAsItems[idx]
                CustomSoundItem(
                    name = sound.title,
                    colorStart = Color(sound.colorStart),
                    colorEnd = Color(sound.colorEnd),
                    isSelected = activeSounds.contains(sound.id),
                    onClick = { viewModel.toggleSound(sound.id) },
                    onLongClick = { deleteConfirmId = sound.id }
                )
            }
        }
        // 导入按钮
        item {
            ImportSoundItem(onClick = {
                pickAudio.launch(arrayOf("audio/*"))
            })
        }
    }

    // 删除确认对话框
    deleteConfirmId?.let { id ->
        val soundName = customAsItems.find { it.id == id }?.title ?: ""
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("删除音频") },
            text = { Text("确定要删除「$soundName」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCustomSound(id)
                    deleteConfirmId = null
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }
}

@Composable
private fun SoundGridItem(
    name: String,
    iconPath: String,
    colorStart: Color,
    colorEnd: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bitmap = rememberIconBitmap(iconPath)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Brush.linearGradient(listOf(colorStart, colorEnd))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = name, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = if (isSelected) colorStart else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomSoundItem(
    name: String,
    colorStart: Color,
    colorEnd: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Brush.linearGradient(listOf(colorStart, colorEnd))
                    else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
                )
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(2),
                color = Color.White,
                fontSize = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = if (isSelected) colorStart else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ImportSoundItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "导入",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "导入音频",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}


