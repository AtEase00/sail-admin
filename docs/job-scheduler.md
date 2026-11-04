# 定时任务与执行记录

2026-11-03 用户批准 Quartz JDBC 方案。首期实现通用任务管理和执行记录；支付补偿接入属于后续第二步，消息 1.5 秒投递循环仍保留原机制。本功能不创建或启用默认示例任务。

## 使用方式

在“系统管理 → 定时任务”新增任务，选择后端注册的处理器，填写参数、Cron 或固定频率间隔、时区和超时阈值。点击“校验并预览未来 5 次”，保存后任务处于暂停状态。启用并等到“已同步”后，调度器开始触发，也可“立即执行”。

首期内置 `system.diagnostic`（调度连通性检查），参数仅为 `{}`，无外部业务副作用。用于确认本系统的调度与执行记录链路。业务任务通过 `sail-job-api` 注册 `JobHandler` Bean，不接收任意类名、方法、SQL、Shell 或脚本。测试慢任务/失败处理器只存在于测试源码，不打入生产 JAR。

“定时任务”页面内包含任务列表、执行记录两个页签。执行记录可以按任务编号、名称/追踪号、状态、来源、创建时间筛选，查看配置快照、触发人、实例、心跳、耗时、成功/失败数量、错误摘要、重试关系和处置依据。任务更新使用版本号；遇到版本冲突先刷新。当前页签每 5 秒刷新列表，切出后停止轮询。

- Cron 使用 Quartz 6/7 字段语法，包含秒。例如 `0 0/5 * * * ?` 为每 5 分钟；时区使用 IANA 名称，如 `Asia/Shanghai`。
- 间隔为**固定频率**，至少 10 秒；不等同于“上一轮完成后再延迟”。既有支付/消息 fixedDelay 机制未被替换。
- 新建默认暂停；暂停取消未开始的排队执行，已经开始的执行继续完成。归档不可恢复，执行中或结果未知的任务不能归档。
- 默认跳过错过的时刻；“恢复补一次”由 Quartz misfire 规则执行，不重放停机期间全部周期。默认 misfire 阈值 10 秒，阈值内的调度延迟不算错过。
- 默认禁用自动重试。只有明确声明幂等可重试的处理器才可配置，最多 3 次，等待按首次延迟逐次翻倍；人工重试只接受当前配置版本的已结束 FAILED/PARTIAL 执行。
- 同一个 JobKey 在集群中禁止并发，同时数据库 `active_run_id` 阻止手动、定时与重试之间的重叠。不同任务定义即使使用同一处理器也不共享互斥锁，业务必须另有幂等键/资源锁。

## 状态与故障语义

任务配置状态：PAUSED / ENABLED / ARCHIVED。调度同步状态：PENDING / SYNCED / ERROR。保存配置不等于调度已生效，独立维护线程每 5 秒对账版本；同步错误保存异常类型，后续继续尝试。

执行状态：QUEUED / RUNNING / SUCCESS / PARTIAL / FAILED / TIMED_OUT / UNKNOWN / SKIPPED。

1. 手动/重试先持久化 QUEUED 与任务占用，再通过维护器派发同一 JobKey 的一次性 Trigger。进程在派发前停止不会丢失排队记录；重复派发由运行状态校验阻止。
2. 开始业务前独立事务写入 RUNNING；业务失败/回滚不回滚执行记录。记录写入失败则不开始业务。完成写入失败时保留占用，后续按失联状态处理。
3. 每秒写入心跳；超时请求协作取消，并保持互斥直到处理器确实返回。超时执行不会自动重试。处理器必须检查 `JobContext.checkCancelled()`，外部 HTTP/数据库操作必须设置自身超时。
4. 默认 90 秒心跳未更新，执行标记为 UNKNOWN，保留任务占用。它既不是“失败”也不是“可安全重试”。管理员须确认旧进程已停止并对账业务结果，填写处置原因后解除阻塞；原记录仍保留 UNKNOWN。
5. 当配置在运行期间发生变化，执行继续使用启动快照，完成后不会按旧配置自动重试。
6. 执行结果摘要由处理器负责脱敏，异常仅对页面暴露类型与追踪号。不得在配置参数、摘要或处置说明中写入凭据。系统不承诺恰好一次执行，业务副作用仍必须幂等。
7. 默认保留 30 天，可设置 1–365 天。每轮最多清理 500 条过期且已结束记录，不清理未结束/占用记录；存在子重试记录时先保留父记录，子记录清理后父记录再清理。任务配置和操作审计不随运行历史一起清理。

## 模块与权限

- `sail-job-api`：处理器、上下文及结构化结果契约。
- `sail-job-server`：配置校验、JDBC 存储、Quartz 同步、执行器、维护线程及 HTTP 接口。
- `sail-server` 装配模块，Quartz 版本跟随 Spring Boot 4.0.9 BOM（2.5.2）。
- `sys_job` 保存任务定义，`sys_job_execution` 保存运行事实，11 张 `QRTZ_*` 表由 Quartz 维护。
- 任务权限：`system:job:query/create/update/control/run`；执行记录：`system:job:log:query`。重试同时要求运行与记录查看权限；失联处置要求控制与记录查看权限。默认超级管理员可访问，不自动授权普通角色。

## 安装、升级与回滚

新建空库：导入最新 `sail-server/src/main/resources/db/base.sql`，已包含任务表和 Quartz schema，共 41 张表。不要再重复导入任务增量脚本。

已有库：先备份，再停止应用或确认本次迁移期间不操作任务；人工执行一次 `scripts/migrations/20261103-job-scheduler.sql`。该脚本只新增 Quartz/业务表和菜单按钮，不删除原表。MySQL DDL 不整体回滚，失败后核对现有结构，不能盲目重复执行整个脚本。迁移后更新后端 JAR 和前端 dist，再重新登录以读取新菜单权限。

始终保留 `spring.quartz.jdbc.initialize-schema=never`，不能改为 `always`，官方初始化脚本会删除已有 Quartz 表。基础脚本中的 Quartz schema 来自 Quartz 2.5.2 MySQL InnoDB 脚本，已去除 DROP。隔离 H2 测试有专门布尔列适配，生产不使用该测试脚本。

关键配置位于 `application.yaml`：

```yaml
spring:
  quartz:
    auto-startup: ${sail.job.enabled:true}
    job-store-type: jdbc
    scheduler-name: sail-scheduler
    jdbc:
      initialize-schema: never
    properties:
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.useProperties: true
      org.quartz.jobStore.clusterCheckinInterval: 10000
      org.quartz.jobStore.misfireThreshold: 10000
      org.quartz.threadPool.threadCount: 4
sail:
  job:
    enabled: true
    maintenance-delay-ms: 5000
    stale-heartbeat-ms: 90000
```

集群实例须使用相同 scheduler-name、同一数据库和一致处理器版本，各实例 ID 唯一，节点时钟同步；不同环境使用不同数据库，不能让开发连接生产调度表。

`--sail.job.enabled=false` 同时关闭该实例 Quartz 自动启动、维护和任务写入/执行入口，不停止其他实例，也不强制中断已经运行的业务。回滚先从管理界面暂停新任务，等待正常执行结束并核查 UNKNOWN，关闭**所有实例**的调度，再回退应用；保留表和历史，不自动删表。本次没有接入支付/消息，回滚不需要切换它们的触发源。

## 开发与验证

新增处理器实现 `JobHandler`，`validate` 返回规范化、不含秘密的参数字符串，`execute` 返回 `JobResult`；失败计数大于零时返回 FAILED 或 PARTIAL，不要捕获异常后统一返回成功。只有业务幂等已确认才覆写 `retryable()`。跨模块接入只依赖 `sail-job-api`，不依赖调度 Server。

自动测试：根目录 Maven `test`，以及 `sail-ui` 中 `pnpm lint:fix`、`pnpm build`、`pnpm test:run`。任务集成测试使用真实 Spring/HTTP/Quartz JDBC + 隔离 H2/Redis 协议测试服务，覆盖权限、校验、并发触发、暂停、配置版本、回滚与执行记录、有限重试、超时、失联处置和保留清理。

双 JVM MySQL 验收使用测试启动类 `sail.job.ClusterVerificationNode`，从单独测试 classpath 加载，不修改生产 JAR。真实环境验收结果见 [交付验收](job-scheduler-acceptance-20261103.md)；生产 Nginx、集群容量和真实业务处理器仍需上线前结合环境验收。
