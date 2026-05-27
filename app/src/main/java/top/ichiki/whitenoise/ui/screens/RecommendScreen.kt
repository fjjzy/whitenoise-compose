package top.ichiki.whitenoise.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.ichiki.whitenoise.data.SceneConfig
import top.ichiki.whitenoise.ui.PlayerViewModel
import top.ichiki.whitenoise.ui.components.rememberSceneBitmap
import top.ichiki.whitenoise.ui.components.scenePathOf

@Composable
fun RecommendScreen(viewModel: PlayerViewModel) {
    val currentScene by viewModel.currentScene.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val sceneStarted by viewModel.sceneStarted.collectAsState()
    val timerMinutes by viewModel.timerMinutes.collectAsState()
    val customScenes by viewModel.customScenes.collectAsState()
    val activeSounds by viewModel.activeSounds.collectAsState()
    val sessionReady by viewModel.sessionReady.collectAsState()
    val scenes = remember(customScenes) { viewModel.allScenes() }
    val pageCount = scenes.size + 1  // 末页是 "+ 新建场景"
    val pagerState = rememberPagerState(initialPage = viewModel.initialPageIndex) { pageCount }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var editingScene by remember { mutableStateOf<SceneConfig?>(null) }

    // 静音图标：仅在有活跃音频且暂停时显示，避免启动时闪烁
    var showPauseIcon by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying, sceneStarted, activeSounds) {
        showPauseIcon = sceneStarted && !isPlaying && activeSounds.isNotEmpty()
    }

    // 场景切换时同步 pager（用户通过其他方式切换场景时）
    LaunchedEffect(currentScene) {
        if (!sessionReady) return@LaunchedEffect
        val idx = scenes.indexOfFirst { it.title == currentScene?.title }
        if (idx >= 0 && pagerState.currentPage != idx) {
            pagerState.scrollToPage(idx)
        }
    }

    // 跳过首次与 initialPage 相同的 settle，避免启动时误触发 browseScene
    var initialSettleSkipped by remember { mutableStateOf(false) }

    // settledPage 仅在 session 恢复完成后才响应，防止启动时闪屏
    LaunchedEffect(pagerState.settledPage, sessionReady) {
        if (!sessionReady) return@LaunchedEffect
        val page = pagerState.settledPage
        if (!initialSettleSkipped && page == viewModel.initialPageIndex) {
            initialSettleSkipped = true
            return@LaunchedEffect
        }
        initialSettleSkipped = true
        if (page < scenes.size) {
            val scene = scenes[page]
            if (sceneStarted) viewModel.playScene(scene) else viewModel.browseScene(scene)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = if (sessionReady) 1f else 0f }
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scenes) {
                    detectTapGestures(
                        onTap = {
                            val page = pagerState.currentPage
                            if (page >= scenes.size) {
                                showCreateDialog = true
                                return@detectTapGestures
                            }
                            val scene = scenes[page]
                            if (isPlaying && sceneStarted) viewModel.pause()
                            else if (sceneStarted && !isPlaying) viewModel.resume()
                            else viewModel.playScene(scene)
                        }
                    )
                },
            beyondBoundsPageCount = 1,
            key = { page -> if (page < scenes.size) scenes[page].title else "__create__" }
        ) { page ->
            if (page < scenes.size) {
                ScenePage(scenes[page])
            } else {
                CreateScenePage(onClick = { showCreateDialog = true })
            }
        }

        AnimatedVisibility(
            visible = showPauseIcon && pagerState.currentPage < scenes.size,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription = "已静音",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        if (pagerState.currentPage < scenes.size) {
            val scene = scenes[pagerState.currentPage]
            val isCustomScene = isCustom(scene)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                SceneInfoOverlay(scene = currentScene)

                // 自定义场景显示三点菜单
                if (isCustomScene) {
                    Spacer(Modifier.height(12.dp))
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                onClick = {
                                    showMenu = false
                                    editingScene = scene
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirm = scene.title
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 36.dp)
        ) {
            IconButton(onClick = { viewModel.showTimerPanel() }) {
                if (timerMinutes > 0) {
                    Text(viewModel.formatTimeRemaining(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                } else {
                    Icon(Icons.Outlined.Timer, contentDescription = "定时", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        val currentPage by remember { derivedStateOf { pagerState.currentPage } }
        SceneIndicator(
            total = pageCount,
            currentPage = currentPage,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }

    if (showCreateDialog) {
        CreateSceneDialog(
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false }
        )
    }

    // 删除确认对话框
    showDeleteConfirm?.let { title ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除场景") },
            text = { Text("确定要删除「$title」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCustomScene(title)
                    showDeleteConfirm = null
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑场景对话框
    editingScene?.let { scene ->
        EditSceneDialog(
            viewModel = viewModel,
            scene = scene,
            onDismiss = { editingScene = null }
        )
    }
}

/** 判断场景是否为自定义场景 */
private fun isCustom(scene: SceneConfig): Boolean {
    return scene.imagePath.startsWith("/") || scene.imagePath.isEmpty()
}

@Composable
private fun ScenePage(scene: SceneConfig) {
    val bitmap = rememberSceneBitmap(scenePathOf(scene))
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = scene.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scene.cropScale,
                        scaleY = scene.cropScale,
                        translationX = scene.cropOffsetX,
                        translationY = scene.cropOffsetY
                    ),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E)))
        }
        // 底部渐变
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )
    }
}

@Composable
private fun CreateScenePage(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1F1B3A), Color(0xFF0E0E1F)))
            )
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("新建场景", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(4.dp))
            Text("自定义图片与音频组合", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SceneInfoOverlay(scene: SceneConfig?, modifier: Modifier = Modifier) {
    scene?.let {
        Column(modifier = modifier) {
            AnimatedContent(
                targetState = it.title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "scene_title"
            ) { title ->
                Text(title, fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Light)
            }
            Spacer(modifier = Modifier.height(4.dp))
            AnimatedContent(
                targetState = it.description,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "scene_desc"
            ) { desc ->
                Text(desc, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun SceneIndicator(total: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(
                        width = 4.dp,
                        height = if (currentPage == index) 16.dp else 8.dp
                    )
                    .background(
                        if (currentPage == index) Color.White
                        else Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
