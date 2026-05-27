package top.ichiki.whitenoise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.ichiki.whitenoise.ui.PlayerViewModel

/**
 * 混音面板 - BottomSheet，支持拖拽小白条收起 + 动画
 */
@Composable
fun MixerSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val activeSounds by viewModel.activeSounds.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // 半透明背景遮罩 — 点击关闭
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onDismiss
            )
    ) {
        // BottomSheet 面板
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                // 拦截点击防止穿透到遮罩 + 支持下拉关闭
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 80f) onDismiss()
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0) dragOffset += dragAmount
                        }
                    )
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = { /* 拦截点击，不穿透 */ }
                ),
            color = Color(0xFF1E1E3A),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // 顶部拖拽小白条
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("混音调节", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (activeSounds.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无正在播放的声音", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeSounds.toList()) { soundId ->
                            val title = viewModel.getSoundById(soundId)?.title ?: soundId
                            val volume = volumes[soundId] ?: 0.5f

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${(volume * 100).toInt()}%", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = volume,
                                    onValueChange = { viewModel.setVolume(soundId, it) },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color(0xFF8B5CF6),
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 定时面板 - BottomSheet，支持拖拽小白条收起 + 动画
 */
@Composable
fun TimerSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val timerMinutes by viewModel.timerMinutes.collectAsState()
    val timerOptions = listOf(5, 10, 15, 20, 25, 30, 45, 60)
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onDismiss
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffset > 80f) onDismiss()
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0) dragOffset += dragAmount
                        }
                    )
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = { /* 拦截点击 */ }
                ),
            color = Color(0xFF1E1E3A),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 拖拽小白条
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("定时关闭", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "关闭", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (timerMinutes > 0) {
                    Text(
                        text = viewModel.formatTimeRemaining(),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.cancelTimer() }) {
                        Text("取消定时", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(timerOptions) { minutes ->
                        val isSelected = timerMinutes == minutes
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) Color(0xFF8B5CF6)
                                    else Color.White.copy(alpha = 0.1f)
                                )
                                .clickable { viewModel.setTimer(minutes) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${minutes}分钟",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
