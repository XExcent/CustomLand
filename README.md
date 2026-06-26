# CustomLand

CustomLand 是一款基于 Android 的截图识别工具，面向“灵动岛”类信息展示场景，能够从外卖、快递、票务、排队、通知等截图中提取核心信息，并生成结构化结果用于通知展示与历史查看。

项目当前提供两种识别路径：

- 视觉模型直识别：直接将截图发送给支持图像输入的模型，输出结构化 JSON。
- OCR + 文本模型两阶段识别：先识别图片文字，再由文本模型提取关键信息，适合主模型不支持视觉输入的场景。

## 功能特性

- 支持通过无障碍服务截取当前屏幕。
- 支持通过快捷设置磁贴一键触发截图识别。
- 支持通过导出的 `TriggerActivity` 供外部应用或自动化工具触发识别。
- 支持视觉模型与 OCR 双路线配置。
- 支持自定义 API 地址、API Key、模型名称与提示词。
- 支持识别结果通知展示，并保留历史记录。
- 支持查看单条识别详情与调试信息。
- 支持可选 Root 辅助恢复无障碍服务。
- 截图仅保存到应用内部存储，不写入系统相册。
- 新增了通过Shizuku API进行截图识别

## 适用场景

- 外卖取餐码
- 快递取件码
- 登机口、检票口、座位号
- 排队叫号
- 各类需要从截图中提取核心凭证并快速展示的通知信息

## 运行要求

- Android 11 及以上
- 最低 SDK：30
- 编译 SDK：36
- Target SDK：36
- Java 11
- Android Studio（建议使用较新稳定版本）

## 技术栈

- Kotlin
- Android View Binding
- Material Design
- Kotlin Coroutines
- Kotlinx Serialization
- MMKV
- Longan
- BlurView
- Gradle Kotlin DSL

## 工作流程

1. 用户通过快捷磁贴或外部 Intent 触发截图。
2. `ScreenshotAccessibilityService` 使用 Android 无障碍截图能力获取当前屏幕。
3. 截图保存到应用内部目录 `files/screenshots/`。
4. `AiRecognizer` 按照当前配置执行识别：
   - 若主模型支持视觉输入，直接发送图片给视觉模型。
   - 若主模型不支持视觉输入，先调用 OCR，再将 OCR 文本交给主模型提取结构化信息。
5. 结果以通知形式展示，并写入历史记录。
6. 用户可以在应用内查看历史结果、详情页和调试信息。

## 快速开始

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd CustomLand
```

### 2. 构建调试包

Windows:

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

生成的调试包通常位于：

`app/build/outputs/apk/debug/`

### 3. 安装并运行

将 APK 安装到 Android 11 及以上设备后，打开应用完成以下配置：

1. 开启无障碍服务。
2. 授予通知权限。
3. 填写 AI 接口地址、API Key、模型名称。
4. 根据模型能力，决定是否启用“主模型支持图像输入”。
5. 如有需要，补充 OCR 接口配置与提示词配置。

## 使用说明

### 方式一：快捷设置磁贴

将应用磁贴添加到系统快捷设置面板，点击后即可触发截图识别。

### 方式二：外部 Intent 调起

应用导出了 `TriggerActivity`，支持其他应用、自动化工具或 `adb` 触发截图识别。

Action：`com.mukapp.customland.TRIGGER_SCREENSHOT`

示例：

```bash
adb shell am start -n com.mukapp.customland/.TriggerActivity -a com.mukapp.customland.TRIGGER_SCREENSHOT
```

说明：

- 如果无障碍服务已经启用，会直接发送截图广播。
- 如果无障碍服务未启用且用户已经开启 Root 辅助，会尝试自动恢复服务。
- 如果服务不可用，会跳转到应用设置页。

### 方式三：应用内查看与管理

- 首页查看识别历史。
- 点击历史项进入详情页。
- 可查看请求体、响应体、耗时等调试信息。

## 配置说明

应用主要通过 MMKV 保存配置，首次启动会自动从旧 `SharedPreferences` 迁移数据。

### 主模型配置

- API 地址
- API Key
- 模型名称
- 是否支持图像输入

默认示例：

- 主模型 API：`https://open.bigmodel.cn/api/paas/v4/chat/completions`
- 主模型 Model：`glm-4v-flash`

### OCR 配置

当“主模型支持图像输入”关闭时，应用会启用 OCR 流程。

- OCR API 地址
- OCR API Key
- OCR 模型名称

默认示例：

- OCR API：`https://api.siliconflow.cn/v1/chat/completions`
- OCR Model：`THUDM/GLM-4.1V-9B-Thinking`

### 提示词配置

支持分别配置以下提示词：

- 图像识别提示词
- OCR 提示词
- OCR 文本提取提示词

默认提示词已经针对灵动岛信息展示场景进行了约束，要求模型输出纯 JSON，并限制字段格式、长度与候选图标类型。

## 权限说明

项目中实际使用到的关键权限如下：

- `android.permission.INTERNET`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.BIND_ACCESSIBILITY_SERVICE`

说明：

- `BIND_ACCESSIBILITY_SERVICE` 需要用户在系统设置中手动启用对应服务。
- 通知权限用于展示识别结果与错误信息。
- 网络权限用于调用 AI / OCR 接口。

## Root 辅助说明

项目支持可选 Root 辅助能力，用于在无障碍服务失效时尝试自动恢复服务状态。

适用场景：

- 设备已经获取 Root 权限。
- 用户明确开启了应用内 Root 开关。
- 无障碍服务因系统原因被关闭，希望自动恢复。

说明：

- Root 不是必需项。
- 未开启 Root 时，应用仍可通过正常无障碍授权完成截图识别。

## 数据存储

- 配置数据：MMKV
- 历史记录：MMKV 独立实例
- 截图文件：应用内部存储 `files/screenshots/`

项目默认会将历史记录数量限制为 50 条。

## 项目结构

```text
app/src/main/java/com/mukapp/customland/
├── BaseApplication.kt                # 应用入口，初始化 MMKV、日志等基础能力
├── TriggerActivity.kt                # 外部触发截图识别的透明 Activity
├── common/
│   ├── Constants.kt                  # 常量定义
│   └── MMKVHelper.kt                 # 配置与历史记录存储
├── logic/
│   ├── AiRecognizer.kt               # AI 识别核心逻辑
│   └── NotificationHandler.kt        # 通知发送与历史管理
├── service/
│   ├── ScreenshotAccessibilityService.kt # 无障碍截图服务
│   └── ScreenshotTileService.kt      # 快捷设置磁贴服务
├── ui/
│   ├── MainActivity.kt               # 主界面与设置页
│   ├── DetailActivity.kt             # 识别结果详情页
│   ├── ScreenshotFragment.kt         # 截图相关页面
│   ├── DebugInfoFragment.kt          # 调试信息页面
│   └── adapter/
│       └── NotificationAdapter.kt    # 历史记录列表适配器
└── utils/                            # 扩展函数、Root 工具、时间工具等
```

## 常用命令

构建调试包：

```bash
./gradlew assembleDebug
```

构建正式包：

```bash
./gradlew assembleRelease
```

运行单元测试：

```bash
./gradlew test
```

运行设备测试：

```bash
./gradlew connectedAndroidTest
```

清理构建产物：

```bash
./gradlew clean
```

## 开发说明

- 项目使用 View Binding，不使用 `findViewById`。
- 异步任务主要通过协程处理。
- 日志输出使用 Longan 的 `logDebug` / `logError` / `logInfo`。
- 依赖仓库使用 `google()`、`mavenCentral()` 与 `jitpack.io`。

## 注意事项

- 无障碍截图能力依赖系统服务，必须先完成授权。
- AI 接口返回内容需要符合约定 JSON 结构，否则会进入错误通知流程。
- 截图和识别结果仅保存在应用内部，不会自动同步到外部存储。
- 如果接入第三方模型服务，请自行确认接口协议与返回格式兼容当前实现。

## License

本项目仓库包含 `LICENSE` 文件，具体授权方式请以仓库中的 `LICENSE` 内容为准。
