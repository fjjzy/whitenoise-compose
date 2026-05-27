# 小米白噪音

一个使用 Kotlin 和 Jetpack Compose 开发的 Android 白噪音应用，移植并复刻小米白噪音的沉浸式体验，支持推荐场景、自选混音、自定义音频与自定义场景。

## 功能特性

- **推荐场景**：内置夏雨、森林、炉火、海洋、夏夜等沉浸式场景。
- **自选混音**：自由组合雨声、溪流、海浪、炉火、鸟鸣、钢琴、时钟等环境音。
- **独立音量**：为每个正在播放的声音单独调节音量。
- **点状音效**：支持青蛙、雨滴、雷声、鸟叫、翻书、海鸥等随机触发音效。
- **定时关闭**：支持设置播放倒计时，到时自动停止。
- **后台播放**：通过前台服务保持后台音频播放，并显示播放通知。
- **自定义内容**：支持导入本地音频、创建自定义场景、选择并裁切场景封面。
- **状态恢复**：保存上次播放场景、自选声音、音量和页面状态。

## 技术栈

- Kotlin
- Jetpack Compose / Material 3
- AndroidX Navigation Compose
- Hilt
- MediaPlayer
- Coil
- Gradle Kotlin DSL

## 项目结构

```text
.
├── app/
│   └── src/main/
│       ├── assets/          # 内置音频、图标和场景图片
│       ├── java/top/ichiki/whitenoise/
│       │   ├── audio/       # 音频引擎和后台播放服务
│       │   ├── data/        # 声音、场景和用户内容持久化
│       │   └── ui/          # Compose 页面、组件和主题
│       └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## 开发环境

- Android Studio Hedgehog 或更新版本
- JDK 17
- Android Gradle Plugin 8.2.0
- Kotlin 1.9.20
- compileSdk 34
- minSdk 26

## 本地运行

1. 克隆仓库：

   ```bash
   git clone <your-repo-url>
   cd xiaomi-whitenoise
   ```

2. 使用 Android Studio 打开项目，等待 Gradle 同步完成。

3. 连接 Android 设备或启动模拟器，运行 `app` 配置。

也可以使用命令行构建调试包：

```bash
./gradlew assembleDebug
```

## 公开前说明

- 本仓库已配置 `.gitignore`，默认忽略 `build/`、`.gradle/`、`local.properties`、APK/AAB 等本地文件和构建产物。
- 本项目包含从小米白噪音移植而来的音频、图片等素材，仅建议用于学习、研究和个人使用。
- 如需公开发布或分发 APK，请先确认相关素材与品牌元素的授权边界。
- 上传 GitHub 前建议先执行一次安全检查和构建验证。

## 免责声明

本项目为个人学习与技术研究项目，与小米公司无关，也未获得小米官方授权。项目中涉及的名称、音频、图片和其他素材版权归原权利方所有。如有侵权，请联系删除。

## 许可证

暂未指定许可证。由于项目包含第三方素材，公开发布前请谨慎选择 LICENSE，并明确代码与素材的授权范围。
