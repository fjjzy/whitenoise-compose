package top.ichiki.whitenoise.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import top.ichiki.whitenoise.data.SceneConfig
import top.ichiki.whitenoise.data.SoundItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 图标内存缓存，避免重复解码
private val iconCache = LruCache<String, ImageBitmap>(40)

// 场景大图内存缓存（增大到12张避免切换时反复解码）
private val sceneCache = LruCache<String, ImageBitmap>(12)

/**
 * 启动时预加载所有场景图片到缓存 — 一次IO批量解码，之后切换零延迟
 */
suspend fun preloadAllSceneBitmaps(context: Context, scenes: List<SceneConfig>) {
    val dm = context.resources.displayMetrics
    withContext(Dispatchers.IO) {
        scenes.forEach { scene ->
            val path = scenePathOf(scene)
            if (sceneCache.get(path) == null) {
                loadAssetBitmapSampled(context, path, dm.widthPixels, dm.heightPixels)
                    ?.also { sceneCache.put(path, it) }
            }
        }
    }
}

fun scenePathOf(scene: SceneConfig): String {
    if (scene.imagePath.isBlank()) return ""
    return if (scene.imagePath.startsWith("/")) scene.imagePath else "scene/${scene.imagePath}"
}

/**
 * 启动时预加载所有图标到缓存 — 避免滚动时主线程同步解码
 */
suspend fun preloadAllIconBitmaps(context: Context, sounds: List<SoundItem>) {
    withContext(Dispatchers.IO) {
        sounds.forEach { sound ->
            val path = "icons/${sound.iconFile}"
            if (iconCache.get(path) == null) {
                try {
                    val inputStream = context.assets.open(path)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    bitmap?.asImageBitmap()?.also { iconCache.put(path, it) }
                } catch (_: Exception) {}
            }
        }
    }
}

/**
 * 场景大图专用 — 优先从缓存读取，缓存未命中则异步解码
 * 使用 Stable key 避免不必要的 recomposition
 */
@Composable
fun rememberSceneBitmap(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    val context = LocalContext.current
    val dm = context.resources.displayMetrics
    // 先查缓存，有则直接用
    val cached = remember(path) { sceneCache.get(path) }
    var bitmap by remember(path) { mutableStateOf(cached) }

    // 缓存未命中则异步解码
    if (bitmap == null) {
        LaunchedEffect(path) {
            bitmap = withContext(Dispatchers.IO) {
                loadAssetBitmapSampled(context, path, dm.widthPixels, dm.heightPixels)
                    ?.also { sceneCache.put(path, it) }
            }
        }
    }

    return bitmap
}

/**
 * 小图标专用 — 先查缓存，未命中则异步解码
 */
@Composable
fun rememberIconBitmap(path: String): ImageBitmap? {
    val context = LocalContext.current
    val cached = iconCache.get(path)
    var bitmap by remember(path) { mutableStateOf(cached) }

    if (bitmap == null) {
        LaunchedEffect(path) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.assets.open(path)
                    val b = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    b?.asImageBitmap()?.also { iconCache.put(path, it) }
                } catch (_: Exception) { null }
            }
        }
    }

    return bitmap
}

private fun loadAssetBitmapSampled(
    context: Context,
    path: String,
    maxWidth: Int,
    maxHeight: Int
): ImageBitmap? {
    return try {
        // 绝对路径走 file 解码（自定义场景图）
        if (path.startsWith("/")) {
            return loadFileBitmapSampled(path, maxWidth, maxHeight)
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }

        val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        val inputStream = context.assets.open(path)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        inputStream.close()
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private fun loadFileBitmapSampled(path: String, maxWidth: Int, maxHeight: Int): ImageBitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxWidth, maxHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(path, decodeOptions)?.asImageBitmap()
    } catch (_: Exception) { null }
}

private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, maxWidth: Int, maxHeight: Int): Int {
    var inSampleSize = 1
    if (rawHeight > maxHeight || rawWidth > maxWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while (halfHeight / inSampleSize >= maxHeight && halfWidth / inSampleSize >= maxWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
