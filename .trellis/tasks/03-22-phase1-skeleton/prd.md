# Phase 1: Project Skeleton & Git Init

## Goal
初始化 git 仓库，创建 Android 项目骨架，搭建 Compose Navigation、Hilt DI、Room 数据库空壳，确保所有页面可导航。

## Requirements
- 初始化 git 仓库，创建 .gitignore
- 创建 Android 项目（Kotlin, Gradle Kotlin DSL, Version Catalog）
- 配置核心依赖：Compose, Material 3, Hilt, Room, DataStore, WorkManager, Retrofit, OkHttp, kotlinx.serialization
- 搭建 Hilt DI 框架（Application + Activity + 基础 Module）
- 搭建 Compose Navigation（4 个底部 Tab + 次级页面路由）
- 每个页面创建空白 Screen + ViewModel 骨架
- 建立分层目录结构：ui/ domain/ data/ ai/ scheduler/ di/ navigation/ util/

## Acceptance Criteria
- [ ] git 仓库已初始化，.gitignore 正确
- [ ] 项目可通过 `./gradlew assembleDebug` 编译
- [ ] 4 个底部 Tab（Today/Tasks/Memory/Settings）可切换
- [ ] 次级页面（Entry/TaskDetail/Notifications/ModelConfig/McpConfig）可跳转和返回
- [ ] Hilt 注入正常工作
- [ ] 所有页面有空白 Screen + ViewModel
- [ ] 目录结构符合 spec/backend/directory-structure.md 和 spec/frontend/directory-structure.md

## Technical Notes
- 包名：com.memorandum
- 最低 SDK：API 26 (Android 8.0)
- 目标 SDK：API 35
- Kotlin 2.0+
- Compose BOM 管理版本
- 单 Activity 架构
