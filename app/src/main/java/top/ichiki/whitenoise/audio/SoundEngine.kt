package top.ichiki.whitenoise.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.random.Random

/**
 * 音频引擎
 *  - LINE: 持续无缝循环播放（双 MediaPlayer 200ms 交叉淡入淡出）
 *  - POINT: 随机间隔触发的一次性音效
 *  - LoudnessEnhancer +600mB (~6dB) 提升基础响度，相当于 2 倍感知音量
 */
@Singleton
class SoundEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 每个 soundId 对应一个无缝循环播放器
    private val loopPlayers = mutableMapOf<String, LoopingPlayer>()
    // 记录每个声音的默认音量系数（归一化 1.0 = 默认）
    private val defaultVolumes = mutableMapOf<String, Float>()

    // 点状音频调度器
    private val pointJobs = mutableMapOf<String, Job>()
    private val pointPlayers = mutableListOf<MediaPlayer>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _volumes = MutableStateFlow<Map<String, Float>>(emptyMap())
    val volumes: StateFlow<Map<String, Float>> = _volumes.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeSounds = MutableStateFlow<Set<String>>(emptySet())
    val activeSounds: StateFlow<Set<String>> = _activeSounds.asStateFlow()

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPaused = false

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        setupAudioFocus()
    }

    private fun setupAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseAll()
                        AudioManager.AUDIOFOCUS_GAIN -> if (isPaused) resumeAll()
                    }
                }
                .build()
        }
    }

    /**
     * 播放线性音频（无缝循环）
     * audioFile 可以是 assets/audio/ 下的文件名（如 "夏雨.mp3"），
     * 也可以是绝对路径（如 /data/.../custom_audio/abc.mp3）
     */
    fun playLine(soundId: String, audioFile: String, volume: Float = 0.5f) {
        if (loopPlayers.containsKey(soundId)) return

        try {
            val source = AudioSource.from(context, audioFile)
            val actualVolume = volume.coerceAtMost(1.0f)
            val player = LoopingPlayer(source, actualVolume, scope)
            player.start()

            loopPlayers[soundId] = player
            defaultVolumes[soundId] = volume
            _activeSounds.value = _activeSounds.value + soundId
            _volumes.value = _volumes.value + (soundId to 1.0f)
            _isPlaying.value = true
            requestAudioFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 启动点状音频调度
     */
    fun startPointAudio(groupId: String, audioNames: List<String>, frequency: Int, volume: Float = 0.5f) {
        if (pointJobs.containsKey(groupId)) return

        val actualVolume = volume.coerceAtMost(1.0f)
        val job = scope.launch {
            while (isActive) {
                val interval = if (frequency > 0) {
                    (60000L / frequency) + Random.nextLong(-2000, 2000)
                } else {
                    Random.nextLong(5000, 15000)
                }
                delay(interval.coerceAtLeast(1000))

                if (isPaused) continue

                val audioName = audioNames.random()
                playOneShot("audio/$audioName.mp3", actualVolume)
            }
        }
        pointJobs[groupId] = job
    }

    private fun playOneShot(assetPath: String, volume: Float) {
        try {
            val afd = context.assets.openFd(assetPath)
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setVolume(volume, volume)
                setOnCompletionListener { mp ->
                    try { mp.release() } catch (_: Exception) {}
                    pointPlayers.remove(mp)
                }
                prepare()
                // 提升点状音效的响度
                attachLoudnessEnhancer(this)
                start()
            }
            afd.close()
            pointPlayers.add(player)
        } catch (_: Exception) {
            // 文件不存在则忽略
        }
    }

    fun stopSound(soundId: String) {
        loopPlayers[soundId]?.release()
        loopPlayers.remove(soundId)
        defaultVolumes.remove(soundId)

        _activeSounds.value = _activeSounds.value - soundId
        _volumes.value = _volumes.value - soundId

        if (loopPlayers.isEmpty() && pointJobs.isEmpty()) {
            _isPlaying.value = false
            abandonAudioFocus()
        }
    }

    fun stopPointAudio(groupId: String) {
        pointJobs[groupId]?.cancel()
        pointJobs.remove(groupId)
    }

    /**
     * 设置音量 — 接收归一化值 (0~1)
     */
    fun setVolume(soundId: String, normalizedVolume: Float) {
        val defaultVol = defaultVolumes[soundId] ?: 1.0f
        val actualVolume = (defaultVol * normalizedVolume).coerceIn(0f, 1f)
        loopPlayers[soundId]?.setVolume(actualVolume)
        _volumes.value = _volumes.value + (soundId to normalizedVolume)
    }

    fun pauseAll() {
        isPaused = true
        loopPlayers.values.forEach { it.pause() }
        pointPlayers.forEach { try { it.pause() } catch (_: Exception) {} }
        _isPlaying.value = false
    }

    fun resumeAll() {
        isPaused = false
        loopPlayers.values.forEach { it.resume() }
        pointPlayers.forEach { try { it.start() } catch (_: Exception) {} }
        if (loopPlayers.isNotEmpty()) {
            _isPlaying.value = true
            requestAudioFocus()
        }
    }

    fun stopAll() {
        loopPlayers.values.forEach { it.release() }
        loopPlayers.clear()
        defaultVolumes.clear()

        pointJobs.values.forEach { it.cancel() }
        pointJobs.clear()

        pointPlayers.forEach { try { it.stop(); it.release() } catch (_: Exception) {} }
        pointPlayers.clear()

        _activeSounds.value = emptySet()
        _volumes.value = emptyMap()
        _isPlaying.value = false
        isPaused = false
        abandonAudioFocus()
    }

    fun toggleSound(soundId: String, audioFile: String, volume: Float = 0.5f) {
        if (loopPlayers.containsKey(soundId)) stopSound(soundId)
        else playLine(soundId, audioFile, volume)
    }

    fun togglePointSound(soundId: String, audioNames: List<String>, frequency: Int, volume: Float = 0.5f) {
        if (pointJobs.containsKey(soundId)) {
            stopPointAudio(soundId)
            _activeSounds.value = _activeSounds.value - soundId
            _volumes.value = _volumes.value - soundId
            if (loopPlayers.isEmpty() && pointJobs.isEmpty()) {
                _isPlaying.value = false
            }
        } else {
            startPointAudio(soundId, audioNames, frequency, volume)
            _activeSounds.value = _activeSounds.value + soundId
            _volumes.value = _volumes.value + (soundId to 1.0f)
            _isPlaying.value = true
            requestAudioFocus()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager?.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        }
    }

    companion object {
        /** +1200mB ≈ +12dB ≈ 4x 感知音量 */
        const val LOUDNESS_GAIN_MB = 1200
    }

    /**
     * 给 MediaPlayer 附加 LoudnessEnhancer 提升响度。
     * 必须在 prepare() 之后调用。
     */
    private fun attachLoudnessEnhancer(player: MediaPlayer): LoudnessEnhancer? {
        return try {
            LoudnessEnhancer(player.audioSessionId).apply {
                setTargetGain(LOUDNESS_GAIN_MB)
                enabled = true
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 音频源 — 屏蔽 assets 与文件路径的差异
     */
    sealed class AudioSource {
        abstract fun apply(player: MediaPlayer)

        data class Asset(val ctx: Context, val path: String) : AudioSource() {
            override fun apply(player: MediaPlayer) {
                val afd = ctx.assets.openFd(path)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
        }

        data class FilePath(val path: String) : AudioSource() {
            override fun apply(player: MediaPlayer) {
                player.setDataSource(path)
            }
        }

        companion object {
            fun from(context: Context, audioFile: String): AudioSource {
                // 绝对路径或文件存在 → FilePath；否则当作 assets/audio/<audioFile>
                return if (audioFile.startsWith("/") && File(audioFile).exists()) {
                    FilePath(audioFile)
                } else {
                    Asset(context, "audio/$audioFile")
                }
            }
        }
    }

    /**
     * 双 MediaPlayer 交叉淡入淡出循环器
     * 在曲尾前 [CROSSFADE_MS] ms 启动第二个实例，互相淡入淡出实现无缝循环
     */
    private inner class LoopingPlayer(
        private val source: AudioSource,
        @Volatile private var targetVolume: Float,
        private val ownerScope: CoroutineScope
    ) {
        private var current: MediaPlayer? = null
        private var enhancer: LoudnessEnhancer? = null
        private var loopJob: Job? = null

        fun start() {
            current = createPlayer(targetVolume).apply { start() }
            scheduleNextLoop()
        }

        private fun createPlayer(volume: Float): MediaPlayer {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                source.apply(this)
                isLooping = false
                setVolume(volume, volume)
                prepare()
            }
            // attach loudness enhancer
            try {
                enhancer?.release()
                enhancer = LoudnessEnhancer(mp.audioSessionId).apply {
                    setTargetGain(LOUDNESS_GAIN_MB)
                    enabled = true
                }
            } catch (_: Exception) {}
            return mp
        }

        private fun scheduleNextLoop() {
            loopJob?.cancel()
            loopJob = ownerScope.launch {
                val mp = current ?: return@launch
                // 等到接近曲尾
                while (isActive) {
                    val duration = try { mp.duration } catch (_: Exception) { -1 }
                    val pos = try { mp.currentPosition } catch (_: Exception) { 0 }
                    if (duration <= 0) {
                        delay(200)
                        continue
                    }
                    val timeToFade = duration - CROSSFADE_MS - pos
                    if (timeToFade <= 0) break
                    delay(min(timeToFade.toLong(), 250L))
                }
                if (!isActive) return@launch
                doCrossfade()
            }
        }

        private suspend fun doCrossfade() {
            val outgoing = current ?: return
            val outgoingEnhancer = enhancer
            val incoming = try {
                createPlayer(0f).apply { start() }
            } catch (_: Exception) {
                // 失败则简单回到开头
                try { outgoing.seekTo(0) } catch (_: Exception) {}
                scheduleNextLoop()
                return
            }
            val newEnhancer = enhancer  // createPlayer 已更新了 enhancer
            current = incoming
            enhancer = newEnhancer

            val steps = 16
            val stepDelay = (CROSSFADE_MS / steps).toLong()
            for (i in 1..steps) {
                if (!coroutineContext.isActive) break
                val r = i / steps.toFloat()
                val v = targetVolume
                try { outgoing.setVolume(v * (1 - r), v * (1 - r)) } catch (_: Exception) {}
                try { incoming.setVolume(v * r, v * r) } catch (_: Exception) {}
                delay(stepDelay)
            }
            try { outgoing.stop() } catch (_: Exception) {}
            try { outgoing.release() } catch (_: Exception) {}
            try { outgoingEnhancer?.release() } catch (_: Exception) {}

            scheduleNextLoop()
        }

        fun setVolume(volume: Float) {
            targetVolume = volume
            try { current?.setVolume(volume, volume) } catch (_: Exception) {}
        }

        fun pause() {
            try { current?.pause() } catch (_: Exception) {}
            loopJob?.cancel()
        }

        fun resume() {
            try { current?.start() } catch (_: Exception) {}
            scheduleNextLoop()
        }

        fun release() {
            loopJob?.cancel()
            try { current?.stop() } catch (_: Exception) {}
            try { current?.release() } catch (_: Exception) {}
            try { enhancer?.release() } catch (_: Exception) {}
            current = null
            enhancer = null
        }
    }
}

private const val CROSSFADE_MS = 800
