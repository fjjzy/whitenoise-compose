package top.ichiki.whitenoise.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用状态 + 用户自定义内容（自定义音频 / 自定义场景）持久化
 */
@Singleton
class UserContentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("whitenoise_state", Context.MODE_PRIVATE)

    // ===== 应用状态 =====
    var selectedTab: Int
        get() = prefs.getInt(KEY_TAB, 0)
        set(v) = prefs.edit().putInt(KEY_TAB, v).apply()

    var sceneStarted: Boolean
        get() = prefs.getBoolean(KEY_SCENE_STARTED, false)
        set(v) = prefs.edit().putBoolean(KEY_SCENE_STARTED, v).apply()

    /** 上次播放的场景标题，null 表示无 */
    var lastSceneTitle: String?
        get() = prefs.getString(KEY_SCENE_TITLE, null)
        set(v) = prefs.edit().putString(KEY_SCENE_TITLE, v).apply()

    /** 自选页激活的声音 ID */
    var customActiveSounds: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_ACTIVE, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_CUSTOM_ACTIVE, v).apply()

    /** 各 sound 的归一化音量 */
    fun saveVolumes(map: Map<String, Float>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDouble()) }
        prefs.edit().putString(KEY_VOLUMES, obj.toString()).apply()
    }

    fun loadVolumes(): Map<String, Float> {
        val s = prefs.getString(KEY_VOLUMES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(s)
            val out = mutableMapOf<String, Float>()
            obj.keys().forEach { k -> out[k] = obj.getDouble(k).toFloat() }
            out
        } catch (_: Exception) { emptyMap() }
    }

    // ===== 用户自定义音频 =====
    fun customAudioDir(): File {
        val dir = File(context.filesDir, "custom_audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadCustomSounds(): List<CustomSound> {
        val s = prefs.getString(KEY_CUSTOM_SOUNDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                CustomSound(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    audioPath = o.getString("audioPath")
                ).takeIf { File(it.audioPath).exists() }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveCustomSounds(list: List<CustomSound>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("audioPath", it.audioPath)
            })
        }
        prefs.edit().putString(KEY_CUSTOM_SOUNDS, arr.toString()).apply()
    }

    // ===== 用户自定义场景 =====
    fun customSceneDir(): File {
        val dir = File(context.filesDir, "custom_scene")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadCustomScenes(): List<CustomScene> {
        val s = prefs.getString(KEY_CUSTOM_SCENES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("audioIds")
                val list = (0 until ids.length()).map { ids.getString(it) }
                CustomScene(
                    title = o.getString("title"),
                    description = o.optString("description", ""),
                    imagePath = o.getString("imagePath"),
                    audioIds = list,
                    cropScale = o.optDouble("cropScale", 1.0).toFloat(),
                    cropOffsetX = o.optDouble("cropOffsetX", 0.0).toFloat(),
                    cropOffsetY = o.optDouble("cropOffsetY", 0.0).toFloat()
                ).takeIf { it.imagePath.isEmpty() || File(it.imagePath).exists() }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveCustomScenes(list: List<CustomScene>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("title", s.title)
                put("description", s.description)
                put("imagePath", s.imagePath)
                put("audioIds", JSONArray(s.audioIds))
                put("cropScale", s.cropScale.toDouble())
                put("cropOffsetX", s.cropOffsetX.toDouble())
                put("cropOffsetY", s.cropOffsetY.toDouble())
            })
        }
        prefs.edit().putString(KEY_CUSTOM_SCENES, arr.toString()).apply()
    }

    companion object {
        private const val KEY_TAB = "selected_tab"
        private const val KEY_SCENE_STARTED = "scene_started"
        private const val KEY_SCENE_TITLE = "scene_title"
        private const val KEY_CUSTOM_ACTIVE = "custom_active"
        private const val KEY_VOLUMES = "volumes"
        private const val KEY_CUSTOM_SOUNDS = "custom_sounds"
        private const val KEY_CUSTOM_SCENES = "custom_scenes"
    }
}

/** 用户导入的自定义音频 */
data class CustomSound(
    val id: String,        // "custom_<uuid>"
    val title: String,
    val audioPath: String  // 绝对路径
)

/** 用户创建的自定义场景 */
data class CustomScene(
    val title: String,
    val description: String,
    val imagePath: String,    // 绝对路径
    val audioIds: List<String>, // 引用 sounds 或 customSounds 的 id
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f
)
