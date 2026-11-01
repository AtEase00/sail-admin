# Sail Admin 前端接入

前端基于 [shadcn-vue-admin](https://github.com/Whbbit1999/shadcn-vue-admin) main 分支源码快照 `31a71ec84d8154486f32755a9e2ab761031a242c`，保留 MIT 许可及原有 Vue / shadcn-vue / Tailwind 组件与主题。源码通过 GitHub codeload ZIP 获取，未建立嵌套 Git 仓库。后端与前端在同一个 Sail Admin 工作目录维护。

## 环境与代理

沿用上游依赖：Vue 3.5、Vue Router 5、Vite 8、TypeScript 6、Tailwind 4、Pinia 4。环境要求 Node.js >=22.15，包管理器固定 pnpm 11.28.6。新增 `qrcode@1.5.4` 本地生成渠道返回内容的二维码，不把交易内容发送给第三方图片服务；对应类型包为 `@types/qrcode@1.5.6`。

复制 `sail-ui/.env.example` 为 `sail-ui/.env.local`。`VITE_SERVER_API_URL` 默认空表示同源，`VITE_SERVER_API_PREFIX=/api`；开发代理目标 `SAIL_BACKEND_URL=http://127.0.0.1:8080`，同时转发 `/api` 和 `/ws`。`SAIL_BACKEND_URL` 仅在 Vite 配置读取，不暴露为浏览器秘密。WebSocket 连接从服务端取得 30 秒一次性票据，不在 URL 传递登录令牌。

本轮已完整安装依赖、构建并启动；后续手工运行命令：

```powershell
Set-Location D:/exploitation/project/sail-admin/sail-ui
corepack pnpm install --frozen-lockfile --ignore-scripts
corepack pnpm dev --host localhost
```

后端开发 YAML 的 `sail.websocket.allowed-origins` 包含 localhost 和 127.0.0.1 的 5173 地址。若使用 `127.0.0.1`、其他端口或实际域名，需显式配置匹配的来源白名单。生产环境使用 HTTPS 同源反向代理，转发 `/api` 和支持 Upgrade 的 `/ws`，静态路由需要回退到 `index.html`。不要把商户私钥、云存储密钥或 `SAIL_MASTER_KEY` 写进 VITE 环境变量。

## 页面与真实接口

| 页面 | 接入行为 |
| --- | --- |
| 登录、工作台、账号 | `/system/auth/login`、`me`、`menus`、`password`、`logout`；无模拟登录、虚构统计 |
| 用户 | `/system/users` 分页查询、新增、编辑、逻辑删除；角色/岗位分配、密码重置、会话及强制下线 |
| 角色 | `/system/roles` 与 `menus/depts` 全量授权；内置角色保护、五类数据范围 |
| 菜单、部门、岗位、字典、参数 | 具名字段表单调用各目录实际接口；父节点与关联目录来自真实数据 |
| 操作审计 | `/system/audit` 分页只读，含 traceId |
| 文件 | `/file/objects` 上传、分页、鉴权 Blob 下载及删除；公开下载必须显式勾选 |
| 存储配置 | `/file/storages` 按本地 / MinIO / 阿里云 OSS 分类展示多套实例，编辑秘密留空保留原值，切换单个默认实例 |
| 我的消息 | `/messages/mine`、`read`、`read-all`、`unread-count`；WebSocket 变化提醒与断线补读 |
| 模板、发送、记录 | `/messages/templates`、`templates/available`、`send`、`history`；明确填写实际接收人编号与全部变量 |
| 支付应用与渠道 | `/pay/applications`、`channels`；管理员录入真实商户资料，响应不返回秘密 |
| 扫码订单 | `/pay/orders`、详情、`sync/resubmit/close/refund`；人民币元精确转为整数分、原业务单号重试、本地二维码及状态轮询 |
| 退款、业务通知 | `/pay/refunds`、`notifications` 分页展示真实处理状态；未接入业务处理器的通知等待状态照常展示 |

发送者仅需 `message:send` 即可读取 `/messages/templates/available`。创建订单使用 `pay:create` 读取 `/pay/channels/available`，返回启用应用下启用渠道的 id、applicationId、name、type；应用列表及订单页面仍需 `pay:query`。这些目录接口没有扩大商户秘密读取权限。

登录令牌保存于 sessionStorage，请求头使用 `Authorization: Bearer ...`。菜单、路由和操作按钮分别检查当前身份与权限，后端继续执行最终权限和数据归属检查。权限加载失败明确报错，不把缺失权限视作管理员。旧会话请求返回 401 不会清除新会话；关闭支付详情会使旧二维码生成结果和轮询结果失效。

上游演示页面及测试源文件保留以便来源追溯，Sail 导航仅列出已接入页面，路由守卫要求 `meta.sail=true`，登录页单独放行；演示路由跳回工作台。演示任务接口不是 Sail 后端契约。前端旧测试不属于本次验证，应用 tsconfig 排除 `__tests__`。

## 本轮交付边界

本轮执行 `pnpm install --frozen-lockfile --ignore-scripts`，未运行安装生命周期脚本；`pnpm build` 的 vue-tsc 与 Vite 构建成功。浏览器真实登录进入工作台，登录、目录查询和消息 WebSocket 经本地开发代理检查通过。登录页修复容器按内容收缩的问题，表单最大宽度 448px，品牌只出现一次，输入与按钮高度 44px，并检查 390px 视口无横向溢出。未运行测试套件；真实支付与云存储尚未联调。


## 2026-11-01 查询与界面验收更新

目录查询已经改为后端 SQL 过滤，客户端传递编号、备注、状态、关键词与时间范围，树形结果保留匹配节点父链。日期范围使用 Ant Design Vue，支持日期、时间、周、月、季度、年；桌面查询四列，侧栏为默认折叠的手风琴，多标签溢出隐藏滚动条并提供箭头。后续完成前端 59 项测试、后端 13 项集成测试及真实本地 CRUD 验收，取代上文早期未运行测试的阶段说明。详细边界见 `ui-acceptance-20261101.md`。


### 工作台只读接口

- `GET /api/system/workbench/audit`：需要 `system:audit:query`，返回服务端最近七个自然日的 `from/to`、逐日 `days`（成功/失败/登录失败次数）、前六种 `actions` 和最近八条 `recent`（带真实操作者资料）。统计截止服务端当前时间，起点包含、终点排除，已逻辑删除审计不计入。
- `GET /api/system/workbench/online`：仅 `SUPER_ADMIN`，返回有效登录账号 `total`、有效会话 `sessionTotal`、按最近登录排序的最多八位 `records`。不包含令牌和会话键，查询不会续期。此处在线表示会话有效，不表示浏览器正在活动。
- 日志统计、在线会话及消息分别显示错误，不使用示例值或把读取失败当作零。页面通过刷新数据重新读取。

## 管理配置与字典接入更新（2026-11-01）

- 后端业务日期时间统一返回 `yyyy-MM-dd HH:mm:ss`，例如 `2026-11-01 14:30:25`；日期范围请求保留原接口区间精度。
- 菜单包含可编辑 `icon`，侧栏和工作台统一按稳定图标名称解析。导航目录来自后端真实层级。
- `/system/configs` 为分组表单；`GET /api/system/configs/settings` 返回字段中文名、分组、提示、输入类型与实际值，`PUT` 接收键值对象并事务保存。`GET /api/system/configs/website` 仅返回已登录用户可见的站点名称与标志。
- 文件存储放入系统配置的「文件存储」组，继续使用 `/api/file/storages`。原 `/files/storages` 路由重定向到此分组。
- `GET /api/system/dictionaries/types` 管理字典类型；`POST /types`、`PUT /types/{id}`、`DELETE /types/{id}` 管理其生命周期。原数据接口支持 `typeCode` 后端过滤，字段增加 `color`、`remark`、`isDefault`、`builtIn`。编辑类型编码时后端事务迁移对应数据。
- `GET /api/system/dictionaries/options` 向已登录业务界面返回启用类型下的启用数据。共享目录合并并发读取，变更后重新加载；读取失败明确展示原值，不把旧缓存当新配置。内置业务值与类型编码禁止更改，标签/颜色/说明可维护。
- 审计接口增加 `requestMethod`、`operationType` 筛选和请求/响应详情。当前版本之后的记录才拥有新增快照；历史空字段保留为空。

验收更新：前端 10 文件 62 项测试、lint/build 和后端 19 项集成测试通过。真实 MySQL/Redis、字典 CRUD、角色回显、图标选择、系统配置与窄屏浏览器结果见 `ui-acceptance-20261101.md`。

### 日期选择类型约束

`DateRangePicker` 的 `mode` 为必填业务配置（date/datetime/week/month/quarter/year），面板不再提供类型切换。公共查询栏的创建时间明确固定为 `datetime`，仅选择日期时间范围；其他字段按业务含义指定对应模式。年月标题切换仍用于快速定位日期，不会改变字段类型。服务端 `createdFrom/createdTo` 继续接收原有本地日期时间参数，无需兼容周/月/年字符串。


## 消息中心接入（2026-11-01）

- `/messages/send`：新建/继续草稿/复制本人历史消息，直写或模板填充，私有文件与图片，范围选择，先预览后确认发布。
- `/messages/history`：任务分页、后端筛选、统计、接收回执抽屉及 CSV 导出；定时取消、撤回、失败重试按权限和实际状态显示。
- `/messages/templates`：模板分页、富文本、变量预览、新增编辑停用与逻辑删除。
- `/messages`：本人收件箱、分类/已读/时间查询、批量已读、确认、归档恢复；顶部铃铛与个人菜单可进入。
- `sail/message-center.ts` 定义共享接口与私有下载；MessageEditor/ContentView 负责编辑与净化渲染，MessageRecipients/Detail/Bell 共享业务交互。富文本依赖 Tiptap，净化依赖 DOMPurify；后端使用 JSoup。
- 新发送页依赖 `message:send`，上传另需 `file:upload`；全体发送、定时、全量查询、回执查询/导出、撤回/取消/重试、模板删除均有独立权限。普通角色应由管理员按职责授予，不自动扩大原角色授权。
- 消息设置在系统配置新分组维护最大人数、定时范围、附件数、撤回时限、提醒入口。调度复用当前应用进程与数据库，不新增中间件。

最终验收以 [消息中心验收记录](message-center-acceptance-20261101.md) 为准，原业务历史消息和旧调用接口继续保留。

## 主动公告与文件预览（2026-11-01）

- 布局挂载 `MessageAnnouncements`，复用唯一消息连接的变化事件；GET `/api/messages/announcements` 返回当前账号最多 10 条有效、未展示的 POPUP 消息，紧急优先。PUT `/api/messages/mine/{id}/presented` 幂等记录真实展示时间，不能代替确认知悉。
- 发布草稿增加 `displayMode`，默认 INBOX，选择 POPUP 时无需进入收件箱即可弹出。复制草稿保留此设置。
- `FileUpload`、`FileAttachment`、`FilePreview` 分别统一待上传列表、已保存附件和预览；消息文件携带 taskId，通过消息授权接口读取。
- 系统配置的标志使用专用图片上传接口；保存配置的事件同步刷新侧栏与 favicon。共享浮层主题统一抽屉、遮罩、日期面板、附件预览和公告层级。
- 258 个菜单图标包含 6 个 Vue 动效版本，中文分类和搜索；稳定名称仍由原菜单接口持久化。

最终 lint/build、前端 76 项测试与后端 31 项测试通过。真实文件、公告和标志同步结果见 [本轮验收](message-preview-acceptance-20261101.md)。

图片交互后续改用轻量 vue-easy-lightbox，保留已有文档解析与 Tiptap 编辑器；侧栏缩窄 20px、收起悬停子菜单和全局主题联动已接入。当前前端验收更新为 19 文件 81 项测试与 lint/build 通过，见 [导航与轻量预览验收](ui-navigation-preview-20261101.md)。

弹窗现统一支持标题拖动、系统内全屏/还原与遮罩防误关闭；个人菜单宽度收紧，114 个上游演示文件及旧导航清理。自动导入类型声明随源码覆盖生成，详见 [弹窗与清理验收](dialog-window-acceptance-20261101.md)。
