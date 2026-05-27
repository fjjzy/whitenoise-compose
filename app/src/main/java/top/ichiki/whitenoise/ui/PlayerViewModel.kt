package top.ichiki.whitenoise.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import top.ichiki.whitenoise.audio.PlaybackService
import top.ichiki.whitenoise.audio.SoundEngine
import top.ichiki.whitenoise.data.CustomScene
import top.ichiki.whitenoise.data.CustomSound
import top.ichiki.whitenoise.data.SceneConfig
import top.ichiki.whitenoise.data.SoundItem
import top.ichiki.whitenoise.data.SoundRepository
import top.ichiki.whitenoise.data.UserContentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val soundEngine: SoundEngine,
    private val store: UserContentStore
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    val isPlaying = soundEngine.isPlaying
    val activeSounds = soundEngine.activeSounds
    val volumes = soundEngine.volumes

    private val _customSounds = MutableStateFlow<List<CustomSound>>(emptyList())
    val customSounds: StateFlow<List<CustomSound>> = _customSounds.asStateFlow()

    private val _customScenes = MutableStateFlow<List<CustomScene>>(emptyList())
    val customScenes: StateFlow<List<CustomScene>> = _customScenes.asStateFlow()

    private val _currentScene = MutableStateFlow<SceneConfig?>(SoundRepository.scenes.firstOrNull())
    val currentScene: StateFlow<SceneConfig?> = _currentScene.asStateFlow()

    private val _sceneStarted = MutableStateFlow(false)
    val sceneStarted: StateFlow<Boolean> = _sceneStarted.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _timerMinutes = MutableStateFlow(0)
    val timerMinutes: StateFlow<Int> = _timerMinutes.asStateFlow()

    private val _timeRemaining = MutableStateFlow(0)
    val timeRemaining: StateFlow<Int> = _timeRemaining.asStateFlow()

    private var timerJob: Job? = null
    private var sceneLoadJob: Job? = null
    private val sceneMutex = Mutex()

    private val _showMixerPanel = MutableStateFlow(false)
    val showMixerPanel: StateFlow<Boolean> = _showMixerPanel.asStateFlow()

    private val _showTimerPanel = MutableStateFlow(false)
    val showTimerPanel: StateFlow<Boolean> = _showTimerPanel.asStateFlow()


    /** UI 可以开始响应用户交互（restore 流程已完成） */
    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    /** Pager 初始页索引 — 在 init 中同步计算，避免 pager 从 0 开始再滚动 */
    var initialPageIndex: Int = 0
        private set

    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private var sessionRestored = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            serviceBound = false
        }
    }

    init {
        bindService()
        // 加载持久化的自定义内容
        _customSounds.value = store.loadCustomSounds()
        _customScenes.value = store.loadCustomScenes()
        // 恢复 tab
        _selectedTab.value = store.selectedTab
        // 同步计算初始页索引，避免 pager 从 page 0 开始再动画滚动
        initialPageIndex = computeInitialPage()
    }

    private fun bindService() {
        Intent(context, PlaybackService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    // ===== 合并访问 =====
    fun allSounds(): List<SoundItem> = SoundRepository.sounds + _customSounds.value.map { it.toSoundItem() }
    fun allScenes(): List<SceneConfig> = SoundRepository.scenes + _customScenes.value.map { it.toSceneConfig() }
    fun getSoundById(id: String): SoundItem? = allSounds().find { it.id == id }
    fun getSceneByTitle(title: String): SceneConfig? = allScenes().find { it.title == title }

    private fun CustomSound.toSoundItem(): SoundItem = SoundItem(
        id = id,
        title = title,
        engTitle = title,
        iconFile = "",
        audioFile = audioPath,
        colorStart = 0xFF8B5CF6,
        colorEnd = 0xFFEC4899,
        defaultVolume = 1.0f,
        isCustom = true
    )

    private fun CustomScene.toSceneConfig(): SceneConfig = SceneConfig(
        title = title,
        description = description,
        engTitle = title,
        engDescription = description,
        imagePath = imagePath,  // 绝对路径，UI 层根据 startsWith("/") 区分
        cropScale = cropScale,
        cropOffsetX = cropOffsetX,
        cropOffsetY = cropOffsetY
    )

    // ===== 会话恢复 =====

    /** 同步计算初始页索引（不涉及音频，仅读 store） */
    private fun computeInitialPage(): Int {
        if (!store.sceneStarted) return 0
        val title = store.lastSceneTitle ?: return 0
        val idx = allScenes().indexOfFirst { it.title == title }
        return if (idx >= 0) idx else 0
    }

    /**
     * 在 UI 第一帧加载完后调用：根据上次状态恢复播放
     */
    fun restoreSession() {
        if (sessionRestored) return
        sessionRestored = true

        val savedVolumes = store.loadVolumes()

        if (store.sceneStarted) {
            val title = store.lastSceneTitle
            val scene = title?.let { getSceneByTitle(it) }
            if (scene != null) {
                _currentScene.value = scene
                _sceneStarted.value = true
                playScene(scene, applyVolumes = savedVolumes)
                _sessionReady.value = true
                return
            }
        }
        // 自选模式：恢复激活声音
        val activeIds = store.customActiveSounds
        if (activeIds.isNotEmpty()) {
            activeIds.forEach { id -> startSoundById(id) }
            // 应用音量
            savedVolumes.forEach { (id, v) -> soundEngine.setVolume(id, v) }
            startService()
        }
        _sessionReady.value = true
    }

    fun autoPlayFirstScene() {
        if (sessionRestored && _sceneStarted.value) return
        if (!sessionRestored) restoreSession()
        if (sessionRestored && (_sceneStarted.value || soundEngine.activeSounds.value.isNotEmpty())) return
        _currentScene.value?.let { playScene(it) }
    }

    // ===== Tab / Scene =====
    fun selectTab(index: Int) {
        _selectedTab.value = index
        store.selectedTab = index
    }

    fun browseScene(scene: SceneConfig) {
        _currentScene.value = scene
    }

    fun playScene(scene: SceneConfig, applyVolumes: Map<String, Float> = emptyMap()) {
        // 如果当前场景音频已在播放中，跳过重复加载
        if (_sceneStarted.value && _currentScene.value?.title == scene.title && applyVolumes.isEmpty()) return

        _currentScene.value = scene
        _sceneStarted.value = true
        store.sceneStarted = true
        store.lastSceneTitle = scene.title
        // 取消上一次还未完成的加载任务
        sceneLoadJob?.cancel()
        sceneLoadJob = viewModelScope.launch {
            sceneMutex.withLock {
                withContext(Dispatchers.IO) {
                    soundEngine.stopAll()
                    loadSceneAudio(scene)
                    if (applyVolumes.isNotEmpty()) {
                        applyVolumes.forEach { (id, v) -> soundEngine.setVolume(id, v) }
                    }
                }
            }
        }
        startService()
    }

    private fun loadSceneAudio(scene: SceneConfig) {
        // 自定义场景
        val custom = _customScenes.value.find { it.title == scene.title }
        if (custom != null) {
            custom.audioIds.forEach { id -> startSoundById(id) }
            return
        }
        // 内置场景
        val audioList = SoundRepository.sceneAudioMap[scene.title] ?: return
        var pointIndex = 0
        audioList.forEach { audio ->
            if (audio.isLineAudio) {
                soundEngine.playLine(audio.audioName, "${audio.audioName}.mp3", audio.volume * 1.0f)
            } else if (audio.isPointAudio && audio.names.isNotEmpty()) {
                soundEngine.startPointAudio(
                    "point_${scene.title}_$pointIndex",
                    audio.names,
                    audio.frequency,
                    audio.volume * 0.8f
                )
                pointIndex++
            }
        }
    }

    private fun startSoundById(id: String) {
        val sound = getSoundById(id) ?: return
        if (sound.isPointAudio) {
            soundEngine.togglePointSound(sound.id, sound.pointAudioNames, sound.frequency, sound.defaultVolume * 1.0f)
        } else {
            // 已存在则跳过
            if (!soundEngine.activeSounds.value.contains(sound.id)) {
                soundEngine.playLine(sound.id, sound.audioFile, sound.defaultVolume * 1.0f)
            }
        }
    }

    fun toggleSound(soundId: String) {
        val sound = getSoundById(soundId) ?: return
        val isCurrentlyPlaying = soundEngine.activeSounds.value.contains(sound.id)

        if (isCurrentlyPlaying) {
            if (_sceneStarted.value) {
                val scene = _currentScene.value
                if (scene != null) {
                    val sceneAudios = SoundRepository.sceneAudioMap[scene.title]
                    val isSceneSound = sceneAudios?.any { it.audioName == sound.id } == true
                    if (isSceneSound) {
                        stopSceneAudio(scene)
                        _sceneStarted.value = false
                        store.sceneStarted = false
                        return
                    }
                }
            }
            if (sound.isPointAudio) {
                soundEngine.togglePointSound(sound.id, sound.pointAudioNames, sound.frequency, sound.defaultVolume * 1.0f)
            } else {
                soundEngine.toggleSound(sound.id, sound.audioFile, sound.defaultVolume * 1.0f)
            }
        } else {
            if (sound.isPointAudio) {
                soundEngine.togglePointSound(sound.id, sound.pointAudioNames, sound.frequency, sound.defaultVolume * 1.0f)
            } else {
                soundEngine.toggleSound(sound.id, sound.audioFile, sound.defaultVolume * 1.0f)
            }
        }

        if (soundEngine.activeSounds.value.isNotEmpty()) startService()
        persistActiveState()
    }

    private fun stopSceneAudio(scene: SceneConfig) {
        val audioList = SoundRepository.sceneAudioMap[scene.title] ?: return
        var pointIndex = 0
        audioList.forEach { audio ->
            if (audio.isLineAudio) {
                soundEngine.stopSound(audio.audioName)
            } else if (audio.isPointAudio) {
                soundEngine.stopPointAudio("point_${scene.title}_$pointIndex")
                pointIndex++
            }
        }
    }

    fun setVolume(soundId: String, volume: Float) {
        soundEngine.setVolume(soundId, volume)
        store.saveVolumes(soundEngine.volumes.value)
    }

    fun pause() {
        soundEngine.pauseAll()
        playbackService?.updateNotification(false)
    }

    fun resume() {
        if (_selectedTab.value == 0 && !_sceneStarted.value) {
            _currentScene.value?.let { playScene(it) }
            return
        }
        soundEngine.resumeAll()
        startService()
        playbackService?.updateNotification(true)
    }

    fun replayCurrentScene() {
        _sceneStarted.value = false
        _currentScene.value?.let { playScene(it) }
    }

    fun stop() {
        soundEngine.stopAll()
        _sceneStarted.value = false
        store.sceneStarted = false
        stopService()
    }

    fun toggleMixerPanel() { _showMixerPanel.value = !_showMixerPanel.value }
    fun hideMixerPanel() { _showMixerPanel.value = false }
    fun toggleTimerPanel() { _showTimerPanel.value = !_showTimerPanel.value }
    fun hideTimerPanel() { _showTimerPanel.value = false }
    fun showMixerPanel() { _showMixerPanel.value = true }
    fun showTimerPanel() { _showTimerPanel.value = true }

    fun setTimer(minutes: Int) {
        _timerMinutes.value = minutes
        _timeRemaining.value = minutes * 60
        timerJob?.cancel()
        if (minutes > 0) {
            timerJob = viewModelScope.launch {
                while (_timeRemaining.value > 0) {
                    delay(1000)
                    _timeRemaining.value -= 1
                }
                stop()
                _timerMinutes.value = 0
            }
            viewModelScope.launch {
                delay(300)
                _showTimerPanel.value = false
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        _timerMinutes.value = 0
        _timeRemaining.value = 0
    }

    fun formatTimeRemaining(): String {
        val m = _timeRemaining.value / 60
        val s = _timeRemaining.value % 60
        return String.format("%02d:%02d", m, s)
    }

    // ===== 自定义音频导入 =====
    fun importCustomSound(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext = displayName.substringAfterLast('.', "mp3").take(5)
                val title = displayName.substringBeforeLast('.').ifBlank { "自定义" }
                val id = "custom_${UUID.randomUUID().toString().take(8)}"
                val target = File(store.customAudioDir(), "$id.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                val sound = CustomSound(id, title, target.absolutePath)
                val list = _customSounds.value + sound
                _customSounds.value = list
                store.saveCustomSounds(list)
            } catch (_: Exception) {}
        }
    }

    fun removeCustomSound(id: String) {
        if (soundEngine.activeSounds.value.contains(id)) soundEngine.stopSound(id)
        val toRemove = _customSounds.value.find { it.id == id } ?: return
        try { File(toRemove.audioPath).delete() } catch (_: Exception) {}
        val list = _customSounds.value.filterNot { it.id == id }
        _customSounds.value = list
        store.saveCustomSounds(list)
    }

    // ===== 音频预览（新建场景对话框用） =====
    private var previewIds = emptyList<String>()

    fun previewAudioSelection(audioIds: List<String>) {
        val toStop = previewIds - audioIds.toSet()
        val toStart = audioIds - previewIds.toSet()
        toStop.forEach { soundEngine.stopSound(it) }
        toStart.forEach { id -> startSoundById(id) }
        previewIds = audioIds
        if (audioIds.isNotEmpty()) startService()
    }

    fun stopPreviewAudio() {
        previewIds.forEach { soundEngine.stopSound(it) }
        previewIds = emptyList()
    }

    // ===== 自定义场景 =====
    fun addCustomScene(imageUri: Uri?, title: String, description: String, audioIds: List<String>, cropScale: Float = 1f, cropOffsetX: Float = 0f, cropOffsetY: Float = 0f) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val safeTitle = title.ifBlank { "我的场景" }
                val id = UUID.randomUUID().toString().take(8)
                val imagePath = if (imageUri != null) {
                    val target = File(store.customSceneDir(), "scene_$id.jpg")
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                } else {
                    ""  // 空路径表示默认深色背景
                }
                val scene = CustomScene(safeTitle, description, imagePath, audioIds, cropScale, cropOffsetX, cropOffsetY)
                val list = _customScenes.value + scene
                _customScenes.value = list
                store.saveCustomScenes(list)
            } catch (_: Exception) {}
        }
    }

    fun removeCustomScene(title: String) {
        val target = _customScenes.value.find { it.title == title } ?: return
        try { File(target.imagePath).delete() } catch (_: Exception) {}
        val list = _customScenes.value.filterNot { it.title == title }
        _customScenes.value = list
        store.saveCustomScenes(list)
    }

    fun updateCustomScene(
        originalTitle: String,
        imageUri: Uri?,
        newTitle: String,
        description: String,
        audioIds: List<String>,
        cropScale: Float = 1f,
        cropOffsetX: Float = 0f,
        cropOffsetY: Float = 0f
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val old = _customScenes.value.find { it.title == originalTitle } ?: return@launch
                val safeTitle = newTitle.ifBlank { "我的场景" }
                val imagePath = if (imageUri != null) {
                    // 删除旧图片
                    if (old.imagePath.isNotEmpty()) try { File(old.imagePath).delete() } catch (_: Exception) {}
                    val id = UUID.randomUUID().toString().take(8)
                    val target = File(store.customSceneDir(), "scene_$id.jpg")
                    context.contentResolver.openInputStream(imageUri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.absolutePath
                } else {
                    old.imagePath  // 保留原有图片
                }
                // 如果没有换图片且没有新裁切参数，保留原有裁切
                val finalCropScale = if (imageUri == null && cropScale == 1f && cropOffsetX == 0f && cropOffsetY == 0f) old.cropScale else cropScale
                val finalCropOffsetX = if (imageUri == null && cropScale == 1f && cropOffsetX == 0f && cropOffsetY == 0f) old.cropOffsetX else cropOffsetX
                val finalCropOffsetY = if (imageUri == null && cropScale == 1f && cropOffsetX == 0f && cropOffsetY == 0f) old.cropOffsetY else cropOffsetY
                val updated = CustomScene(safeTitle, description, imagePath, audioIds, finalCropScale, finalCropOffsetX, finalCropOffsetY)
                val list = _customScenes.value.map { if (it.title == originalTitle) updated else it }
                _customScenes.value = list
                store.saveCustomScenes(list)
            } catch (_: Exception) {}
        }
    }

    private fun persistActiveState() {
        val active = soundEngine.activeSounds.value
        store.customActiveSounds = active
        store.saveVolumes(soundEngine.volumes.value)
    }

    /** 由 Activity onPause 调用 */
    fun persistAll() {
        persistActiveState()
        store.selectedTab = _selectedTab.value
        _currentScene.value?.let { store.lastSceneTitle = it.title }
        store.sceneStarted = _sceneStarted.value
    }

    private fun startService() {
        Intent(context, PlaybackService::class.java).also { intent ->
            context.startService(intent)
        }
    }

    private fun stopService() {
        Intent(context, PlaybackService::class.java).also { intent ->
            context.stopService(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (serviceBound) {
            context.unbindService(serviceConnection)
        }
        timerJob?.cancel()
    }
}
