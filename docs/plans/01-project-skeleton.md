# 模块 01 - 项目骨架与基础设施

## 1. 目标
搭建 Android 项目基础结构，建立分层架构、依赖注入、导航框架，为后续所有模块提供统一的工程基座。

## 2. 技术选型
- 语言：Kotlin 2.0+
- 最低 SDK：API 26 (Android 8.0)
- 目标 SDK：API 35
- 构建：Gradle Kotlin DSL + Version Catalog
- DI：Hilt
- UI：Jetpack Compose + Material 3
- 导航：Compose Navigation (Type-safe)
- 异步：Kotlin Coroutines + Flow

## 3. 项目结构

```
app/src/main/java/com/memorandum/
├── MemorandumApp.kt              # Application，Hilt 入口
├── MainActivity.kt               # 单 Activity
├── navigation/
│   ├── AppNavGraph.kt            # 顶层导航图
│   ├── BottomNavBar.kt           # 底部导航栏组件
│   └── Routes.kt                 # 路由定义（sealed class / object）
├── ui/
│   ├── today/
│   ├── entry/
│   ├── tasks/
│   ├── taskdetail/
│   ├── memory/
│   ├── notifications/
│   ├── settings/
│   └── common/                   # 公共 UI 组件
├── domain/
│   └── usecase/
├── data/
│   ├── local/
│   │   ├── room/
│   │   └── datastore/
│   └── remote/
│       ├── llm/
│       └── mcp/
├── ai/
│   ├── prompt/
│   └── schema/
├── scheduler/
└── di/                           # Hilt Module 定义
    ├── DatabaseModule.kt
    ├── NetworkModule.kt
    ├── DataStoreModule.kt
    └── AiModule.kt
```

## 4. 详细任务

### 4.1 创建 Android 项目
- 使用 Gradle Kotlin DSL
- 配置 Version Catalog (`libs.versions.toml`)
- 统一管理所有依赖版本

### 4.2 配置核心依赖
```toml
# libs.versions.toml 关键依赖
[versions]
kotlin = "2.1.0"
compose-bom = "2025.01.00"
hilt = "2.53"
room = "2.7.1"
datastore = "1.1.3"
workmanager = "2.10.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinx-serialization = "1.7.3"
compose-navigation = "2.8.6"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "compose-navigation" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
```

### 4.3 搭建 Hilt DI 框架
- `MemorandumApp` 添加 `@HiltAndroidApp`
- `MainActivity` 添加 `@AndroidEntryPoint`
- 创建基础 Module：`DatabaseModule`、`NetworkModule`、`DataStoreModule`

### 4.4 搭建 Compose Navigation
- 定义路由：

```kotlin
sealed class Route(val route: String) {
    // 底部 Tab
    object Today : Route("today")
    object Tasks : Route("tasks")
    object Memory : Route("memory")
    object Settings : Route("settings")

    // 次级页面
    object Entry : Route("entry")
    object TaskDetail : Route("task_detail/{taskId}") {
        fun create(taskId: String) = "task_detail/$taskId"
    }
    object Notifications : Route("notifications")
    object ModelConfig : Route("model_config")
    object McpConfig : Route("mcp_config")
}
```

- 底部导航栏：Today / Tasks / Memory / Settings
- 次级页面通过 NavHost 内部路由跳转

### 4.5 建立分层架构约定
- `ui/` 层：Composable + ViewModel，不直接访问 Room/网络
- `domain/` 层：UseCase，封装业务逻辑，依赖 Repository 接口
- `data/` 层：Repository 实现、Room DAO、DataStore、LLM/MCP 客户端
- `ai/` 层：Prompt 模板管理、JSON Schema 定义、AI 编排器

### 4.6 基础 UI 脚手架
- 每个页面先创建空白 Composable + 对应 ViewModel
- 底部导航可正常切换
- 次级页面可正常跳转和返回

## 5. 验收标准
- [ ] 项目可编译运行
- [ ] 4 个底部 Tab 可正常切换
- [ ] 次级页面可正常跳转
- [ ] Hilt 注入正常工作
- [ ] Room 数据库可初始化（空表）
- [ ] DataStore 可读写

## 6. 依赖关系
- 无前置依赖，这是第一个模块
- 后续所有模块依赖此模块
