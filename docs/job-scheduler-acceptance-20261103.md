# 定时任务一期交付验收

日期：2026-11-03。根据用户批准的 [Quartz JDBC 选型评审](scheduler-selection-review-20261103.md) 实施通用任务管理与执行记录。代码、本地迁移、真实服务和页面验收已完成，未部署生产。支付补偿接入保留第二阶段，消息高频投递继续使用原机制。

## 交付内容

新增 `sail-job-api` / `sail-job-server`，由 Spring Boot 4.0.9 装配 BOM 管理的 Quartz 2.5.2。任务定义与执行事实分别存于 `sys_job` / `sys_job_execution`，Quartz JDBC 使用 11 张 InnoDB 表。

前端入口为“系统管理 → 定时任务 / 执行记录”。支持处理器白名单、参数校验、Cron/固定频率和时区、执行预览、启停/立即执行/归档、分页检索与快照详情、有限重试、协作超时、失联处置及分批保留清理。复用现有权限、操作审计、列表偏好、标签缓存和页面过渡。

生产代码仅内置无业务副作用的 `system.diagnostic` 处理器，不自动创建任务。新任务默认暂停，自动重试默认关闭。测试慢任务/故障处理器只位于测试源码，未打入生产 JAR。

## 自动化验证

| 检查 | 结果 |
| --- | --- |
| Maven package（含后端完整测试） | 40 项通过：既有框架 32 项 + 任务集成 8 项 |
| 前端 Vitest | 24 文件，100 项通过 |
| `pnpm lint:fix` | 通过 |
| `pnpm build` | 类型检查和生产构建通过；保留既有包体积提示 |
| `git diff --check` | 通过 |

任务集成测试使用真实 Spring HTTP、Quartz JDBC 和 H2 隔离数据源，覆盖匿名/普通用户访问、Cron/时区/参数校验、持久化、并发手工执行、配置版本冲突、暂停取消排队、运行中改配置、业务事务回滚仍保留失败记录、有限退避重试、超时、失联占用和过期清理。前端测试覆盖权限、启用/同步/占用条件、请求字段白名单、版本与确认流程、保存错误保留输入。

## 真实 MySQL 故障演练

使用独立数据库 `sail_job_verify`、独立 Redis namespace 和端口 18082/18083 启动两个不同 JVM；没有停止用户的 MySQL 或 Redis。演练结束后已停止测试 JVM。

1. 两个 JVM 加入同一个 Quartz JDBC 集群。
2. 两节点同时手动触发同一任务：一次接受、另一次 HTTP 409，只有一条执行记录。
3. 10 秒固定频率触发耗时 12 秒的处理器：运行时间区间不重叠。
4. 关闭并重启两个节点，暂停状态保留。人为制造超过 misfire 阈值的旧触发时间：SKIP 不补跑，FIRE_ONCE 只补一次。
5. 强制结束正在执行的 JVM：存活节点将失联记录标记 UNKNOWN，拒绝重复执行；确认原进程退出并填写说明后，存活节点可执行下一次，原 UNKNOWN 记录保留。
6. 仅阻断验证 JVM 的 MySQL TCP 代理连接：断连期间不能开始新执行；处理器协作取消但完成记录写入失败时，恢复后保留 UNKNOWN，不能伪造成功或自动放开占用。停止旧进程、重启并人工处置后，执行恢复成功。
7. 250 个暂停任务跨越 200 条内部对账分页边界，全部同步成功；本机重新对账约 1.83 秒。第 13 页（每页 20）返回最后 10 条，未产生执行记录。这是小规模分页边界验证，**不是生产吞吐或容量上限测试**。

结果摘要保存在 [双节点报告](../output/verification/job-cluster.json) 和 [断连与分页报告](../output/verification/job-resilience.json)。双 JVM 启动器为测试源码 `sail.job.ClusterVerificationNode`；本次代理故障脚本位于本机 `/tmp/sail-admin-tools/`，没有进入生产包。

## 本地升级与页面验收

已停止旧后端，备份 `sail_admin` 后执行一次增量迁移。备份位置 `/tmp/sail-admin-tools/sail-admin-before-jobs.sql`（仅本机所有者可读写，含原业务数据，请勿提交仓库）。主库由 28 张表增加至 41 张表，原 MySQL 账号密码及 Redis 无密码配置沿用。

新版后端在 8081、前端在 5173 运行，health 为 UP。使用真实登录和真实接口，在浏览器完成：新增诊断任务、预览未来 5 次、保存默认暂停、启用并等待同步、Cron 自动触发、立即执行、暂停、按任务查看记录和详情。两条记录分别来自定时和手动触发，均为 SUCCESS。验收任务 `本地验收·调度连通性检查` 已恢复暂停，保留供查看；没有持续运行的示例任务。

桌面 1440×1000、移动端 390×844 检查页面及详情。表格在容器内横向滚动，移动页面本身无横向溢出。

- [桌面任务列表](../output/playwright/jobs-desktop.png)
- [移动任务列表](../output/playwright/jobs-mobile.png)
- [任务配置与 Cron 预览](../output/playwright/jobs-editor-desktop.png)
- [桌面执行记录](../output/playwright/job-executions-desktop.png)
- [桌面运行详情](../output/playwright/job-execution-detail-desktop.png)
- [移动执行记录](../output/playwright/job-executions-mobile.png)
- [移动运行详情](../output/playwright/job-execution-detail-mobile.png)

## 上线边界

生产需先备份并执行一次任务增量脚本，再配套更新前后端，重新登录刷新权限。不得对已有库重新导入 base.sql，也不得启用 Quartz 自动重建 schema。真实生产反向代理、生产规模和未来业务处理器的幂等/超时仍需按环境验收。本次没有外部告警通知集成；超时和失联通过任务状态、运行记录及服务日志反馈。

完整参数、处理器接入和关闭/回滚步骤见 [使用与运维说明](job-scheduler.md)，接口与权限见 [API 文档](api.md)。
