# LyricNotes

<p align="center">
  <b>专为 Spotify 与流媒体打造的安卓端实时双语歌词与深度典故伴侣</b>
</p>

<p align="center">
  <a href="https://github.com/MarcoWong06-12/LyricNotes/actions"><img src="https://img.shields.io/github/actions/workflow/status/MarcoWong06-12/LyricNotes/build-apk.yml?branch=main&label=Build%20APK&logo=github" alt="Build Status"></a>
  <a href="https://github.com/MarcoWong06-12/LyricNotes/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?logo=android" alt="Platform"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-purple.svg?logo=kotlin" alt="Kotlin"></a>
</p>

---

## 📖 项目简介

**LyricNotes** 是一款专为数字流媒体音乐爱好者打造的 Android 伴侣应用。当你在手机上使用 **Spotify** 或系统播放器听歌时，LyricNotes 能自动捕获播放曲目与实时时间轴，呈现如 Apple Music 般灵动沉浸的流体动态氛围光效与双语滚动歌词。

不仅如此，LyricNotes 深度整合了 **Genius 乐评典故与歌曲创作故事（Behind The Lyrics）**、**多源歌词并发竞速**、**冷门歌曲翻译自动补齐**以及**歌词脏字屏蔽彻底还原**，带你探索音乐背后的深层文化与艺术原貌。

---

## ✨ 核心特性

### 1. 🎵 Spotify 实时同屏与双语滚动歌词
- **即开即连**：通过系统通知监听（`NotificationListenerService`）与广播接收器，自动捕获 Spotify 及各大播放器的切歌与播放/暂停状态。
- **毫秒级防漂移时间轴**：内置本地时间戳推算引擎，滑动与拖动进度条丝滑响应，告别歌词延迟与卡顿。
- **双语对齐显示**：外文歌曲原文与中文翻译同屏平滑滚动，并支持全专繁简体中文无损切换。

### 2. 🎨 Apple Music 灵感流体动态氛围光效
- **自适应动态光晕**：依据当前曲目专辑封面的主色调与色彩分布，实时渲染全屏流体渐变与环境弥散光。
- **视线聚焦排版**：当前正在播放的歌词行自动放大并带有专属光晕辉光，未播放行优雅淡化。

### 3. 💡 Genius 深度典故与歌词创作故事（Behind The Lyrics）
- **重点歌词精解**：带有背景故事或文化隐喻的歌词行，右侧标有 `[💡 典故]` 标识，轻触即可唤起抽屉式精解面板。
- **中英双语对照**：上方呈现地道中文深度赏析，下方紧跟 Genius 英文官方引文与小字原文，兼顾阅读体验与原意考证。
- **全曲背景故事（Song Story）**：顶部一键展开该曲目的创作缘由、灵感来源与幕后制作花絮。

### 4. ⚡ 多源歌词并发竞速与智能翻译补齐
- **多源竞速检索**：聚合网易云音乐、QQ 音乐、酷狗音乐及全球开源歌词库 LRCLIB 等，多源并发拉取，优先匹配带有高精度时间轴的双语歌词。
- **缺失翻译智能补齐**：遇到无官方翻译的冷门小众外文曲目时，自动调用内置分块翻译引擎，在保护时间戳结构的前提下极速补齐中文翻译。
- **日语假名注音（Furigana）**：智能识别日语汉字并渲染振假名注音，方便跟唱学习。

### 5. 🛡️ 歌词审查脱敏与脏字彻底还原
- **还原艺术本貌**：针对国内音乐源中常见的英文脏字掩码打星（如 `f***`、`b****`）及机翻错误，内置语义校正与 Genius 引文对齐引擎。
- 自动将屏蔽词还原为真实歌词与真实译文，并持久化写回本地数据库。

### 6. 💿 实体 CD 唱架与经典小册子模式保留
- 完整保留经典的虚拟 CD 唱架（CD Shelf）与唱片内页小册子（Booklet）浏览模式，兼顾实体唱片收藏与流媒体随行听歌。

---

## 🛠️ 技术架构

项目基于现代 Android 开发规范构建，采用模块化与单向数据流设计：

- **开发语言**：Kotlin 1.9+
- **UI 框架**：Jetpack Compose, Material 3
- **架构模式**：MVI / MVVM, Clean Architecture
- **依赖注入**：Hilt / Dagger
- **异步处理**：Kotlin Coroutines, StateFlow, SharedFlow
- **本地存储**：Room Database（歌词、典故与翻译缓存持久化）
- **网络通信**：Retrofit 2, OkHttp 3, Kotlinx Serialization
- **图片加载**：Coil (Compose)
- **CI / CD**：GitHub Actions 持续集成与自动化构建

---

## 📲 安装与构建

### 方式一：通过 GitHub Actions 体验最新测试构建（推荐）

本项目已接入 GitHub Actions 自动化 CI 流水线：
1. 访问本仓库的 [Actions 页面](https://github.com/MarcoWong06-12/LyricNotes/actions)。
2. 点击最新一次成功的流水线（`Build LyricNotes APK`）。
3. 在页面底部的 **Artifacts** 区域下载 `LyricNotes-v1.0.0-APK` 压缩包，解压后即可获得安装包。

> **提示**：LyricNotes 独立包名为 `com.lyricnotes.app`，安装后不会覆盖或影响原有的 LinerNotes 应用。

### 方式二：本地编译

需要环境：JDK 17、Android SDK (API 34)：

```bash
# 1. 克隆代码仓库
git clone https://github.com/MarcoWong06-12/LyricNotes.git

# 2. 进入项目目录
cd LyricNotes

# 3. 编译 Debug APK
./gradlew assembleDebug

# 4. 或编译 Release APK
./gradlew assembleRelease
```

编译生成的安装包位于：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 📄 开源许可

本项目基于 [MIT License](LICENSE) 开源。
