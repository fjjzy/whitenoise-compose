package top.ichiki.whitenoise.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.ichiki.whitenoise.data.SoundRepository
import top.ichiki.whitenoise.ui.PlayerViewModel
import top.ichiki.whitenoise.ui.components.MixerSheet
import top.ichiki.whitenoise.ui.components.TimerSheet
import top.ichiki.whitenoise.ui.components.preloadAllSceneBitmaps
import top.ichiki.whitenoise.ui.components.preloadAllIconBitmaps
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")

@Composable
fun MainScreen(viewModel: PlayerViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val showMixer by viewModel.showMixerPanel.collectAsState()
    val showTimer by viewModel.showTimerPanel.collectAsState()
    val activeSounds by viewModel.activeSounds.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val pagerState = rememberPagerState(initialPage = selectedTab) { 2 }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
    }

    // 预加载所有图片 + 自动播放推荐场景
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        preloadAllSceneBitmaps(context, viewModel.allScenes())
        preloadAllIconBitmaps(context, SoundRepository.sounds)
        viewModel.autoPlayFirstScene()
    }

    // 推荐页不显示底栏
    val showBottomBar = selectedTab == 1 && activeSounds.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A2E))) {
        // 页面内容 — 关闭 overscroll 拉伸效果
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondBoundsPageCount = 1
            ) { page ->
                when (page) {
                    0 -> RecommendScreen(viewModel = viewModel)
                    1 -> CustomScreen(viewModel = viewModel)
                }
            }
        }

        // 顶部 Tab 栏 - 透明悬浮，字体小，紧贴状态栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            val tabs = listOf("推荐", "自选")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            viewModel.selectTab(index)
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // 底部工具栏 - 仅自选页且有声音时显示
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomToolBar(
                activeSoundNames = activeSounds.mapNotNull { viewModel.getSoundById(it)?.title },
                isPlaying = isPlaying,
                onPlayPause = { if (isPlaying) viewModel.pause() else viewModel.resume() },
                onMixerClick = { viewModel.toggleMixerPanel() },
                onTimerClick = { viewModel.toggleTimerPanel() }
            )
        }

        // 混音 BottomSheet - 带动画
        AnimatedVisibility(
            visible = showMixer,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            MixerSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.hideMixerPanel() }
            )
        }

        // 定时 BottomSheet - 带动画
        AnimatedVisibility(
            visible = showTimer,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            TimerSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.hideTimerPanel() }
            )
        }
    }
}

@Composable
private fun BottomToolBar(
    activeSoundNames: List<String>,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onMixerClick: () -> Unit,
    onTimerClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF16213E).copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = onMixerClick
                    )
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeSoundNames.take(3).joinToString(" · "),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
            IconButton(onClick = onMixerClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Tune, "混音", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onTimerClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Timer, "定时", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
