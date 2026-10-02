# LinerNotes（唱片内页）

LinerNotes 是一款专为实体 CD 与数字流媒体音乐爱好者打造的安卓端歌词与唱片资料伴侣应用。

应用支持通过蓝牙连接便携式 CD 播放机实时读取播放状态，拉取双语滚动歌词，并深度整合了 Genius 乐评典故与 Discogs 实体唱片版本数据。

---

## 核心特性

### 1. 实体 CD 伴侣与双语歌词同步
- 支持通过蓝牙连接便携式 CD 播放机（如山灵 SyncLink 协议）。
- 实时获取播放机当前曲目号、时间戳与播放状态，实现歌词毫秒级平滑跟随滚动。
- 支持双语歌词逐行对齐展示与歌词微调偏移行。

### 2. 多源歌词检索与智能翻译补全
- 多数据源并发检索：聚合网易云音乐、QQ 音乐、酷狗音乐以及国际开源歌词库 LRCLIB、Musixmatch。多源并发竞速拉取，优先匹配带有高精度时间轴的版本。
- 缺失自动翻译补齐：对于无翻译的曲目，系统自动调用内置分块翻译引擎，在严格保护时间戳与排版的前提下极速补齐中文翻译。
- 繁简体一键互转：支持全专歌词与背景资料在简体中文与繁体中文之间无损切换。

### 3. Genius 典故注释与双语对照排版
- 深度整合 Genius 歌曲创作背景、文化隐喻与俚语注释。
- 采用紧凑双语排版，中文解析下方对应英文原文，兼顾阅读体验与原意参考。

### 4. 歌词脱敏与脏字审查还原
- 针对国内音乐源中常见的纯星号与词中掩码，内置确定性匹配与 Genius 引文对齐引擎。
- 自动将屏蔽词还原为真实歌词与汉语翻译，并自动持久化写回本地 Room 数据库。

### 6. 系统原生媒体通知栏与锁屏
- 完整接入 Android MediaSession 架构。
- 支持在系统通知栏与锁屏界面查看封面、曲目信息与进度，并提供原生切歌与暂停控制。

### 7. Discogs 唱片版本元数据检索
- 支持扫描或输入实体唱片条形码检索 Discogs 数据库。
- 查看当前实体专辑的发行国家、厂牌、年份、压盘批次等资料。

---

## 更多 CD 播放机适配支持

目前应用已完整适配山灵（Shanling）支持 SyncLink 蓝牙协议的便携 CD 播放机。

如果你正在使用其他品牌或型号的便携 CD 播放机，且该播放机拥有官方的手机配套控制 App，如果你希望 LinerNotes 也适配你的播放机型号：

欢迎在本项目提一个 Issue（https://github.com/MarcoWong06-12/LinerNotes/issues ），并把官方配套 App 的安装包（APK）分享给我。我会分析其中的蓝牙通信数据包与控制协议，尽力把协议逆向并集成进 LinerNotes:)

---

## 下载与安装

前往项目的 Releases 页面下载最新安装包：
https://github.com/MarcoWong06-12/LinerNotes/releases

- 正式版安装包：app-release.apk
- 调试版安装包：app-debug.apk

系统要求：Android 8.0（API 26）及以上。

---

## 技术架构

- 核心语言：Kotlin
- 界面框架：Jetpack Compose, Material 3
- 架构设计：MVI / MVVM, Clean Architecture
- 依赖注入：Hilt
- 本地数据库：Room Database
- 网络请求：Retrofit 2, OkHttp 3, Kotlinx Serialization
- 图片加载：Coil
- 硬件协议：Bluetooth RFCOMM (Shanling SyncLink 协议逆向与解析)

---

## 本地编译

项目采用标准 Gradle 构建系统：

1. 克隆代码库：
   git clone https://github.com/MarcoWong06-12/LinerNotes.git

2. 使用 JDK 17 编译 APK：
   ./gradlew assembleRelease

编译生成的安装包位于：app/build/outputs/apk/release/app-release.apk

---

## 开源协议

本项目采用 MIT 协议开源。
