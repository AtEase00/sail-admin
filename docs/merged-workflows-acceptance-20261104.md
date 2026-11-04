# 工作流页面合并验收

日期：2026-11-04。

- 定时任务内使用“任务列表 / 执行记录”页签；行内“记录”打开该任务的记录筛选。筛选项复用一张 `QueryBar`，切出页签后停止轮询。
- 发布记录内使用“发布消息”抽屉，复用原编辑器；草稿编辑、复制、保存、未保存关闭确认均保留。发布或关闭后刷新记录。
- 删除两个独立导航入口，旧地址保留重定向和查询参数。合并入口允许原任一权限进入，各项操作继续独立鉴权。
- 旧库依次执行已有迁移后，再执行 `scripts/migrations/20261104-merge-workflow-pages.sql`；新库使用更新后的 `db/base.sql`。菜单 ID、权限标识和角色授权不变。本地已迁移，未部署生产。

验证：前端 115 项测试、lint、构建通过；后端 40 项集成测试通过。独立 MySQL 验收库执行迁移两次，权限和角色关系一致。浏览器验证旧链接、行内记录入口、统一筛选、抽屉关闭确认、真实草稿保存/编辑/复制；验收草稿已删除，未发送消息。桌面与 390px 手机端均无页面横向溢出。

截图：`output/playwright/merged-job-executions.png`、`merged-message-compose.png`、`merged-job-executions-mobile.png`、`merged-message-compose-mobile.png`。
