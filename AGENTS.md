# AGENTS.md

## 项目概述

CustomLand 是一款 Android 应用，使用 AI 技术识别和提取截图中的信息。支持视觉模型直接识别，也支持 OCR + 文本模型两阶段识别。

- **包名**: `com.mukapp.customland`
- **最低 SDK**: 30 (Android 11)
- **语言**: Kotlin
- **UI**: View Binding + Material Design

## 构建命令

- 构建调试包: `./gradlew assembleDebug`
- 构建正式包: `./gradlew assembleRelease`
- 运行测试: `./gradlew test`
- 清理构建: `./gradlew clean`

## 项目结构

```
app/src/main/java/com/mukapp/customland/
├── BaseApplication.kt       # 应用入口，初始化 MMKV、Logger
├── TriggerActivity.kt       # 外部触发截图识别的透明 Activity
├── common/                  # 常量和 MMKV 存储助手
├── logic/
│   ├── AiRecognizer.kt      # 核心 AI 识别逻辑（视觉/OCR）
│   └── NotificationHandler.kt
├── service/
│   ├── ScreenshotAccessibilityService.kt  # 无障碍服务截图
│   └── ScreenshotTileService.kt           # 快捷设置磁贴
├── ui/                      # Activity、Fragment、Adapter
└── utils/                   # 扩展函数和工具类
```

## 代码风格

- 使用 Kotlin 惯用写法和扩展函数
- 遵循 Android Kotlin 风格指南
- 使用 View Binding（不用 `findViewById`）
- 使用协程处理异步操作（避免 `GlobalScope`，使用 `applicationScope`）
- 使用 Longan 的 `logDebug`/`logError` 打印日志

## 依赖库

- **MMKV**: 腾讯高性能 KV 存储
- **Longan**: Kotlin Android 工具库 (DylanCaiCoding)
- **BlurView**: 背景模糊效果 (Dimezis)
- **Kotlinx Serialization**: JSON 序列化
- **refreshVersions**: 依赖版本管理

## 核心组件

### AiRecognizer (`logic/AiRecognizer.kt`)
核心 AI 识别，两种模式：
1. 视觉模型：直接分析图片
2. OCR + 文本模型：两阶段识别

### ScreenshotAccessibilityService
使用 Android 无障碍 API 截取屏幕，触发方式：
- 快捷设置磁贴
- 外部 Intent: `com.mukapp.customland.TRIGGER_SCREENSHOT`

## 测试

运行设备测试: `./gradlew connectedAndroidTest`
运行单元测试: `./gradlew test`

## 重要说明

- 无障碍服务需要用户在系统设置中手动启用
- AI API 地址和密钥通过 MMKV 配置
- 截图仅保存到应用内部存储
