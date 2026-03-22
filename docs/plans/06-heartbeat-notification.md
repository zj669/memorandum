# 模块 06 - 心跳与通知

## 1. 目标
实现后台心跳机制和本地通知系统。WorkManager 负责周期性巡检，AlarmManager 负责精确时间提醒，客户端保留去重、静默时段和频率保护等硬约束。

## 2. 架构

```
scheduler/
├── HeartbeatWorker.kt            # WorkManager 周期性心跳
├── AlarmScheduler.kt             # AlarmManager 精确提醒
├── AlarmReceiver.kt              # Alarm 广播接收器
├── NotificationHelper.kt         # 通知构建与发送
├── NotificationActionReceiver.kt # 通知动作处理
├── QuietHoursChecker.kt         # 静默时段判断
├── CooldownManager.kt           # 冷却时间管理
└── DeduplicationChecker.kt      # 通知去重
```

## 3. WorkManager 心跳

### 3.1 HeartbeatWorker
```kotlin
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val heartbeatOrchestrator: HeartbeatOrchestrator
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = heartbeatOrchestrator.executeHeartbeat()
            when (result) {
                is HeartbeatResult.Notified -> Result.success()
                is HeartbeatResult.Skipped -> Result.success()
                is HeartbeatResult.Failed -> {
                    // 非致命错误，仍然 success 避免 WorkManager 退避
                    // 记录错误日志
                    Result.success()
                }
            }
        } catch (e: Exception) {
            // LLM 配置缺失等情况，不重试
            Result.success()
        }
    }
}
```

### 3.2 心跳调度管理
```kotlin
class HeartbeatScheduleManager @Inject constructor(
    private val workManager: WorkManager,
    private val appPreferencesDataStore: AppPreferencesDataStore
) {
    companion object {
        const val HEARTBEAT_WORK_NAME = "memorandum_heartbeat"
    }

    /**
     * 启动/更新心跳调度
     * 根据用户选择的频率档位设置间隔
     */
    fun scheduleHeartbeat(frequency: HeartbeatFrequency) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            frequency.intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            HEARTBEAT_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 暂停心跳
     */
    fun pauseHeartbeat() {
        workManager.cancelUniqueWork(HEARTBEAT_WORK_NAME)
    }

    /**
     * 查询心跳状态
     */
    fun observeHeartbeatStatus(): Flow<WorkInfo?>
}
```

### 3.3 频率档位
```kotlin
enum class HeartbeatFrequency(val intervalMinutes: Long) {
    LOW(120),      // 2 小时
    MEDIUM(60),    // 1 小时
    HIGH(30)       // 30 分钟
}
```

## 4. AlarmManager 精确提醒

### 4.1 AlarmScheduler
```kotlin
class AlarmScheduler @Inject constructor(
    private val context: Context,
    private val alarmManager: AlarmManager
) {
    /**
     * 为特定时间块注册精确提醒
     * 提前 10 分钟提醒
     */
    fun scheduleTaskAlarm(
        taskId: String,
        taskTitle: String,
        triggerAtMillis: Long,
        notificationTitle: String,
        notificationBody: String
    ) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("title", notificationTitle)
            putExtra("body", notificationBody)
            putExtra("action_type", NotificationActionType.OPEN_TASK.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
            // 无权限时降级为 WorkManager 近似提醒
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        }
    }

    /**
     * 取消任务的精确提醒
     */
    fun cancelTaskAlarm(taskId: String)

    /**
     * 任务完成/放弃时清理所有相关 Alarm
     */
    fun cancelAllAlarmsForTask(taskId: String)
}
```

### 4.2 AlarmReceiver
```kotlin
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var notificationRepository: NotificationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra("task_id") ?: return
        val title = intent.getStringExtra("title") ?: return
        val body = intent.getStringExtra("body") ?: return
        val actionType = intent.getStringExtra("action_type") ?: return

        // 发送通知
        // 写入通知记录
    }
}
```

## 5. 通知系统

### 5.1 NotificationHelper
```kotlin
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_TASK = "memorandum_task"
        const val CHANNEL_HEARTBEAT = "memorandum_heartbeat"
        const val CHANNEL_RISK = "memorandum_risk"
    }

    /**
     * 初始化通知渠道（Application.onCreate 调用）
     */
    fun createChannels() {
        // CHANNEL_TASK: 任务提醒，默认重要性
        // CHANNEL_HEARTBEAT: 心跳提醒，低重要性
        // CHANNEL_RISK: 风险提醒，高重要性
    }

    /**
     * 发送通知
     */
    fun send(
        id: Int,
        title: String,
        body: String,
        channelId: String,
        taskRef: String?,
        actionType: NotificationActionType
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(taskRef, actionType))
            .addAction(buildSnoozeAction(id, taskRef))
            .addAction(buildMarkDoneAction(id, taskRef))
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * 根据 NotificationType 选择渠道
     */
    fun channelForType(type: NotificationType): String = when (type) {
        NotificationType.DEADLINE_RISK -> CHANNEL_RISK
        NotificationType.HEARTBEAT_CHECK -> CHANNEL_HEARTBEAT
        else -> CHANNEL_TASK
    }

    private fun buildContentIntent(taskRef: String?, actionType: NotificationActionType): PendingIntent
    private fun buildSnoozeAction(notificationId: Int, taskRef: String?): NotificationCompat.Action
    private fun buildMarkDoneAction(notificationId: Int, taskRef: String?): NotificationCompat.Action
}
```

### 5.2 NotificationActionReceiver
```kotlin
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> {
                // 1 小时后重新提醒
                // 更新 notification 记录 snoozed_until
                // 取消当前通知
            }
            ACTION_MARK_DONE -> {
                // 更新任务状态为 DONE
                // 记录 task_event
                // 取消当前通知
                // 触发记忆更新
            }
        }
    }
}
```

## 6. 客户端硬保护

### 6.1 QuietHoursChecker
```kotlin
class QuietHoursChecker @Inject constructor(
    private val appPreferencesDataStore: AppPreferencesDataStore
) {
    /**
     * 判断当前是否在静默时段
     * 默认 23:00 - 07:00
     */
    suspend fun isInQuietHours(): Boolean

    /**
     * 高风险截止提醒可突破静默时段
     */
    fun canOverrideQuietHours(type: NotificationType): Boolean {
        return type == NotificationType.DEADLINE_RISK
    }
}
```

### 6.2 CooldownManager
```kotlin
class CooldownManager @Inject constructor(
    private val taskDao: TaskDao
) {
    /**
     * 检查任务是否在冷却期内
     */
    suspend fun isInCooldown(taskId: String): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        val cooldownUntil = task.notificationCooldownUntil ?: return false
        return System.currentTimeMillis() < cooldownUntil
    }

    /**
     * 设置冷却期
     * AI 建议 cooldown_hours，客户端裁剪到 [1, 24] 范围
     */
    suspend fun setCooldown(taskId: String, hours: Int) {
        val clampedHours = hours.coerceIn(1, 24)
        val until = System.currentTimeMillis() + clampedHours * 3600_000L
        taskDao.updateCooldown(taskId, until)
    }
}
```

### 6.3 DeduplicationChecker
```kotlin
class DeduplicationChecker @Inject constructor(
    private val notificationDao: NotificationDao
) {
    /**
     * 检查是否重复通知
     * 同一任务 + 同一类型，在 4 小时内不重复
     */
    suspend fun isDuplicate(
        taskRef: String,
        type: NotificationType,
        windowMs: Long = 4 * 3600_000L
    ): Boolean {
        val since = System.currentTimeMillis() - windowMs
        val recent = notificationDao.getRecentForTaskAndType(taskRef, type, since)
        return recent.isNotEmpty()
    }
}
```

### 6.4 保护链执行顺序
```
HeartbeatOrchestrator 输出 should_notify=true
  ↓
1. QuietHoursChecker: 是否在静默时段？（DEADLINE_RISK 可突破）
2. CooldownManager: 任务是否在冷却期？
3. DeduplicationChecker: 是否重复通知？
4. 任务状态检查: DONE/DROPPED 的任务不通知
  ↓
全部通过 → 发送通知
任一拦截 → 记录跳过原因到心跳日志
```

## 7. 通知动作处理

### 7.1 点击通知
```kotlin
// Deep Link 路由
// OPEN_TASK -> memorandum://task/{taskId}
// OPEN_TODAY -> memorandum://today
// 在 MainActivity 中处理 Intent，导航到对应页面
```

### 7.2 稍后提醒
- 默认延后 1 小时
- 通过 AlarmScheduler 注册新的精确提醒
- 更新 notification 记录的 snoozed_until

### 7.3 快速标记完成
- 更新 task.status = DONE
- 记录 TaskEvent(DONE)
- 取消该任务的所有 Alarm
- 异步触发 MemoryOrchestrator

## 8. 权限管理

### 8.1 所需权限
```xml
<!-- 通知权限 (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 精确闹钟 (Android 12+) -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

<!-- 前台服务（如需要） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 开机自启（恢复 Alarm） -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### 8.2 权限请求流程
```kotlin
class PermissionManager @Inject constructor(
    private val context: Context
) {
    fun hasNotificationPermission(): Boolean
    fun hasExactAlarmPermission(): Boolean

    // 在 Settings 页面展示权限状态
    // 引导用户授权
}
```

### 8.3 BootReceiver
```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 重新注册所有活跃任务的 Alarm
            // 重新启动 WorkManager 心跳
        }
    }
}
```

## 9. 用户忽略行为追踪

### 9.1 策略
```kotlin
class NotificationBehaviorTracker @Inject constructor(
    private val notificationDao: NotificationDao
) {
    /**
     * 统计用户对某类通知的忽略率
     * 连续忽略 3 次同类通知 -> 提高该类通知的发送阈值
     */
    suspend fun getIgnoreRate(type: NotificationType, windowDays: Int = 7): Float

    /**
     * 是否应该提高发送阈值
     */
    suspend fun shouldRaiseThreshold(type: NotificationType): Boolean
}
```

## 10. 验收标准
- [ ] WorkManager 心跳按配置频率正常触发
- [ ] AlarmManager 精确提醒在指定时间触发
- [ ] 通知可正常显示，包含标题、内容、动作按钮
- [ ] 点击通知可跳转到对应页面
- [ ] 稍后提醒可正常延后
- [ ] 快速标记完成可更新任务状态
- [ ] 静默时段内普通通知被拦截
- [ ] 冷却期内重复通知被拦截
- [ ] 去重逻辑正确
- [ ] DONE/DROPPED 任务不再触发通知
- [ ] 开机后 Alarm 和心跳可恢复
- [ ] 通知权限引导正常

## 11. 依赖关系
- 前置：模块 01（骨架）、模块 02（数据层）、模块 04（AI 编排 - HeartbeatOrchestrator）
- 协作：模块 05（心跳中可能调用 MCP）
- 被依赖：模块 07（通知响应触发记忆更新）
