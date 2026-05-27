package top.ichiki.whitenoise.data

/**
 * 声音数据模型 - 严格按照原版 APK 配置
 */

data class SoundItem(
    val id: String,
    val title: String,
    val engTitle: String,
    val iconFile: String,
    val audioFile: String,   // assets/audio/ 下的文件名 或 绝对路径
    val colorStart: Long,
    val colorEnd: Long,
    val defaultVolume: Float,
    val isPointAudio: Boolean = false,
    val frequency: Int = 0,
    val pointAudioNames: List<String> = emptyList(),
    val isCustom: Boolean = false  // 自定义导入的音频
)

data class SceneConfig(
    val title: String,
    val description: String,
    val engTitle: String,
    val engDescription: String,
    val imagePath: String,
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f
)

data class SceneAudio(
    val audioName: String,
    val isLineAudio: Boolean,
    val isPointAudio: Boolean,
    val names: List<String>,
    val volume: Float,
    val frequency: Int,
    val duration: Long
)

object SoundRepository {

    // 自选声音 - 线性音频（持续循环）
    val sounds: List<SoundItem> = listOf(
        SoundItem("夏雨", "夏雨", "Shower", "夏雨.png", "夏雨.mp3", 0xFF8FC86F, 0xFF46A773, 1.4f),
        SoundItem("雨打树叶", "雨打树叶", "Rainforest", "雨打树叶.png", "雨打树叶.mp3", 0xFFFF8DA5, 0xFF9B85EC, 1.5f),
        SoundItem("河流", "河流", "River", "河流.png", "河流.mp3", 0xFF84C1F3, 0xFF79A7EF, 1.35f),
        SoundItem("溪流", "溪流", "Stream", "溪流.png", "溪流.mp3", 0xFFFFB586, 0xFFFE9090, 1.65f),
        SoundItem("近海", "近海", "Beach", "近海.png", "近海.mp3", 0xFF40A9B7, 0xFF74D8BD, 1.0f),
        SoundItem("远海", "远海", "Sea", "远海.png", "远海.mp3", 0xFFF89BAE, 0xFFF7A78B, 1.0f),
        SoundItem("炉火", "炉火", "Fireplace", "炉火.png", "炉火.mp3", 0xFF7B86FF, 0xFFFF93E8, 1.8f),
        SoundItem("森林", "森林", "Grove", "森林.png", "森林.mp3", 0xFFFFC685, 0xFFFF8282, 1.8f),
        SoundItem("鸟鸣", "鸟鸣", "Birds A", "鸟鸣.png", "鸟鸣.mp3", 0xFFB77FD4, 0xFFFD96AD, 1.6f),
        SoundItem("海风", "海风", "Monsoon", "海风.png", "空气.mp3", 0xFF91DB9D, 0xFF3AB2A3, 2.0f),
        SoundItem("钢琴声", "钢琴声", "Piano A", "钢琴声.png", "钢琴.mp3", 0xFF6A79D2, 0xFFE790B0, 1.4f),
        SoundItem("窗外的风", "窗外的风", "Wind B", "窗外的风.png", "窗外的风.mp3", 0xFFAE6DE7, 0xFF6CB6E7, 2.0f),
        SoundItem("时钟", "时钟", "Clock", "时钟.png", "时钟.mp3", 0xFF8FC86F, 0xFF46A773, 1.6f),
        // === 新增线性音频 ===
        SoundItem("庭院鸟鸣", "庭院鸟鸣", "Garden Birds", "庭院鸟鸣.png", "庭院鸟鸣.mp3", 0xFF4CAF50, 0xFF8BC34A, 1.5f),
        SoundItem("蝉鸣", "蝉鸣", "Cicada", "蝉鸣.png", "蝉鸣.mp3", 0xFFFF9800, 0xFFFFC107, 1.4f),
        SoundItem("细雨", "细雨", "Light Rain", "细雨.png", "细雨.mp3", 0xFF90CAF9, 0xFF64B5F6, 1.5f),
        SoundItem("火车", "火车", "Train", "火车.png", "火车.mp3", 0xFF8D6E63, 0xFFA1887F, 1.3f),
        SoundItem("火车人声", "火车人声", "Train Voices", "火车人声.png", "火车人声.mp3", 0xFFCE93D8, 0xFFBA68C8, 1.2f),
        SoundItem("夏夜的风", "夏夜的风", "Summer Wind", "夏夜的风.png", "夏夜的风.mp3", 0xFF80DEEA, 0xFF4DD0E1, 1.8f),
        SoundItem("学习环境", "学习环境", "Study Ambience", "学习环境.png", "学习环境.mp3", 0xFFA5D6A7, 0xFF81C784, 1.4f),
        SoundItem("KOTO琴", "KOTO琴", "Koto", "KOTO 琴.png", "KOTO琴.mp3", 0xFFEF9A9A, 0xFFE57373, 1.3f),
        SoundItem("图书馆大厅", "图书馆大厅", "Library Hall", "图书馆大厅.png", "图书馆大厅.mp3", 0xFFB0BEC5, 0xFF90A4AE, 1.2f)
    )

    // 推荐页的 5 个场景
    val scenes: List<SceneConfig> = listOf(
        SceneConfig("夏雨", "六月与夏雨的邂逅", "Rain", "A sunshiny shower", "夏雨.jpg"),
        SceneConfig("森林", "把你的秘密藏进森林", "Forest", "Find peace among the trees", "森林.jpg"),
        SceneConfig("炉火", "温暖冬日的闲暇时光", "Fireplace", "Sit by the fireplace with a cup of tea", "炉火.jpg"),
        SceneConfig("海洋", "海边漫步的气息", "Ocean", "Have a walk on the beach", "海洋.jpg"),
        SceneConfig("夏夜", "夏夜虫鸣的陪伴", "Night", "A quiet summer night", "夏夜.jpg")
    )

    // 场景音频配置
    val sceneAudioMap: Map<String, List<SceneAudio>> = mapOf(
        "夏雨" to listOf(
            SceneAudio("河流", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("夏雨", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("雨打树叶", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("", false, true, listOf("青蛙1","青蛙2","青蛙3","青蛙4","青蛙5"), 1.0f, 15, 3082),
            SceneAudio("", false, true, listOf("雨滴1","雨滴2","雨滴3","雨滴4","雨滴5","雨滴6","雨滴7","雨滴8"), 1.0f, 44, 2064),
            SceneAudio("", false, true, listOf("雷1","雷2","雷3"), 1.0f, 5, 8046)
        ),
        "森林" to listOf(
            SceneAudio("鸟鸣", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("森林", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("溪流", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("", false, true, listOf("庄稼地1","庄稼地2","庄稼地3","庄稼地4","庄稼地5"), 1.0f, 15, 3082),
            SceneAudio("", false, true, listOf("鸟1","鸟2","鸟3","鸟4","鸟5","鸟6","鸟7","鸟8","鸟9","鸟10"), 1.0f, 25, 2064),
            SceneAudio("", false, true, listOf("风吹树叶1","风吹树叶2","风吹树叶3","风吹树叶4","风吹树叶5"), 1.0f, 4, 8046)
        ),
        "炉火" to listOf(
            SceneAudio("时钟", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("炉火", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("窗外的风", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("钢琴", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("", false, true, listOf("火焰啪啪1","火焰啪啪2","火焰啪啪3","火焰啪啪4"), 1.0f, 15, 3082),
            SceneAudio("", false, true, listOf("翻书1","翻书2","翻书3","翻书4","翻书5"), 1.0f, 5, 8046)
        ),
        "海洋" to listOf(
            SceneAudio("空气", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("近海", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("远海", true, false, emptyList(), 1.0f, 0, 0),
            SceneAudio("", false, true, listOf("快艇"), 1.0f, 5, 8046),
            SceneAudio("", false, true, listOf("海浪1","海浪2","海浪3","海浪4","海浪5","海浪6"), 1.0f, 44, 2064),
            SceneAudio("", false, true, listOf("海鸥1","海鸥2","海鸥3","海鸥4","海鸥5"), 1.0f, 15, 3082),
            SceneAudio("", false, true, listOf("船笛1","船笛2"), 1.0f, 5, 8046)
        ),
        "夏夜" to listOf(
            SceneAudio("森林", true, false, emptyList(), 0.6f, 0, 0),
            SceneAudio("窗外的风", true, false, emptyList(), 0.4f, 0, 0),
            SceneAudio("", false, true, listOf("庄稼地1","庄稼地2","庄稼地3","庄稼地4","庄稼地5"), 0.8f, 10, 4000),
            SceneAudio("", false, true, listOf("青蛙1","青蛙2","青蛙3","青蛙4","青蛙5"), 0.7f, 12, 3000)
        ),
        "庭院" to listOf(
            SceneAudio("鸟鸣", true, false, emptyList(), 0.8f, 0, 0),
            SceneAudio("溪流", true, false, emptyList(), 0.5f, 0, 0),
            SceneAudio("", false, true, listOf("鸟1","鸟2","鸟3","鸟4","鸟5","鸟6","鸟7","鸟8","鸟9","鸟10"), 0.9f, 20, 2500),
            SceneAudio("", false, true, listOf("风吹树叶1","风吹树叶2","风吹树叶3","风吹树叶4","风吹树叶5"), 0.6f, 5, 7000)
        ),
        "图书馆" to listOf(
            SceneAudio("时钟", true, false, emptyList(), 0.7f, 0, 0),
            SceneAudio("钢琴", true, false, emptyList(), 0.3f, 0, 0),
            SceneAudio("", false, true, listOf("翻书1","翻书2","翻书3","翻书4","翻书5"), 0.8f, 8, 6000)
        ),
        "旅行" to listOf(
            SceneAudio("远海", true, false, emptyList(), 0.5f, 0, 0),
            SceneAudio("窗外的风", true, false, emptyList(), 0.6f, 0, 0),
            SceneAudio("空气", true, false, emptyList(), 0.4f, 0, 0)
        )
    )

    fun getSoundById(id: String): SoundItem? = sounds.find { it.id == id }
    fun getSceneByTitle(title: String): SceneConfig? = scenes.find { it.title == title }
}
