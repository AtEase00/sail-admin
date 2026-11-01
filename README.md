# Sail Admin

Sail Admin 是面向企业后台管理员和业务操作人员的管理系统基础工程，将账号权限、组织资料、站内消息、文件和扫码收款放在同一个后台中。管理员可以维护用户与授权、发布消息并查看送达回执、管理文件和存储配置；业务人员只能访问获授权的菜单与数据。页面读写真实后端接口，服务端再次校验权限和资源归属。

项目由 JDK 21 / Spring Boot 4 后端和 Vue 3 管理前端组成。后端采用 Maven 模块化单体，按 Controller、Service、Mapper 分层；前端通过 HTTP 与 WebSocket 接入。当前适合开发和本地验证，正式部署及外部支付、对象存储需要结合实际环境完成配置和联调。

![当前 Sail Admin 工作台，展示业务概览、操作日志和常用入口](docs/screenshots/readme-dashboard.jpg)

> 以下图片于 2026-11-01 从当前本地运行的管理前端重新截取。页面中的统计、账号和文件来自截图时的本地环境，不代表其他部署环境的数据。

## 功能范围

| 模块 | 已实现的主要能力 |
| --- | --- |
| 系统管理 | 登录与锁定、会话下线、用户/角色/菜单按钮授权、部门和岗位、五类数据范围、字典、系统参数、登录与操作审计 |
| 消息中心 | 模板和草稿、富文本及私有附件、按组织选择与排除收件人、预览人数、定时发布、分批投递、公告弹窗、回执与导出、阅读确认、归档、撤回和重试；WebSocket 提醒与数据库收件箱配合 |
| 文件管理 | 上传、鉴权预览和下载、归属校验、显式公开读取；本地磁盘、MinIO、阿里云 OSS 可配置多套实例，并选择新文件的默认存储 |
| 扫码支付 | 微信 Native、支付宝当面付、业务单号幂等、查单、关单、整单退款、回调验签、定时补偿和持久化业务通知 |
| 管理前端 | 工作台、权限导航、系统管理、消息、文件及支付页面；中英文界面、响应式布局、主题设置和可拖动/全屏的弹窗 |

典型使用顺序是：初始化管理员 → 建立部门、岗位和用户 → 配置角色的菜单权限与数据范围 → 由业务人员上传文件或发布消息 → 在工作台、消息回执和操作审计中查看结果。支付应用和渠道可随后接入具体业务订单；没有真实商户资料时，支付页面可管理配置和查看记录，但不能完成真实交易联调。

支付金额以人民币分为单位。业务模块应按自己的订单计算金额，并通过 `PaymentEventHandler` 幂等消费支付通知；未接入处理器时通知保留等待状态。当前不包含钱包、分账或部分退款。通用任务管理平台、多租户、邮件短信及 MFA 不在当前实现范围内。`sail-gateway` 只保留模块位置，首期无需独立启动。

## 界面展示

### 角色与权限

管理员在角色页面分配菜单和按钮权限，并选择全部、自定义部门、本部门、本部门及下级或仅本人五类数据范围。前端根据授权展示导航，后端仍在接口和数据查询时执行校验。

![当前角色授权页的菜单与按钮权限树](docs/screenshots/readme-role-permissions.jpg)

### 消息发布与公告

发布者可以保存草稿、选择模板和收件范围，预览实际人数后发布；发布记录保留投递、阅读和确认回执。需要主动展示的公告可在接收方页面弹出，展示记录与主动确认分别保存。

![当前消息发布页面，包含富文本正文、发布设置和接收范围](docs/screenshots/readme-message-compose.jpg)

### 文件与存储

文件列表支持权限内的上传、预览、下载与删除。图片使用全屏预览；PDF、Word、Excel、Markdown 等按各自格式解析。历史文件仍从上传时记录的存储配置读取。

![当前系统配置中的文件存储页面，按本地磁盘、MinIO 和阿里云 OSS 分类](docs/screenshots/readme-storage.jpg)

## 技术与目录

| 部分 | 当前基线 |
| --- | --- |
| 后端 | JDK 21、Spring Boot 4.0.9、Maven 3.9.11、MyBatis-Plus 3.5.18、Sa-Token 1.46.0 |
| 数据与缓存 | MySQL 8.4、Redis 7 为目标基线；开发环境支持配置中的旧 Redis 兼容模式 |
| 前端 | Vue 3、TypeScript、Vite 8、Tailwind CSS 4、shadcn-vue、Pinia；Node.js >= 22.15，pnpm 11.28.6 |

```text
sail-admin/
├── sail-dependencies/        # 第三方及内部模块版本管理
├── sail-common/              # core、web、test 公共能力
├── sail-framework/           # web、security、mybatis、redis、log、oss
├── sail-modules/             # system、message、file、pay；各含 api/server
├── sail-server/              # 单体启动入口、环境配置、基础 SQL
├── sail-gateway/             # 预留模块，不作为当前部署入口
├── sail-ui/                  # Vue 管理前端
├── scripts/                  # 本地启动及已有库增量脚本
├── docs/                     # 接口资料、历史验收记录及页面截图
└── mvnw.ps1                  # 固定 Maven 3.9.11 的 PowerShell 入口
```

后端入口是 `sail.server.SailApplication`，可执行产物为 `sail-server/target/sail-server-1.0.0.jar`。`sail-server` 装配四个业务模块的 Server 实现；各 API 模块承载跨模块 Java 契约，Controller、实体和 Mapper 留在所属模块内。数据库保存业务事实，Redis 保存会话、临时票据及实时变化信号；消息收件箱与支付通知持久化，用于断线补读或失败重试。服务端控制权限和数据范围，前端隐藏无权限入口只是交互层处理。

## 本地启动

以下步骤在工程根目录执行，使用 PowerShell 7。MySQL、Redis 的安装和启动由操作者完成；应用不会自动创建数据库或导入 SQL。

1. 准备 JDK 21、PowerShell 7、MySQL 和 Redis。配置 `JAVA_HOME` 指向 JDK 21，并准备空的 `sail_admin` 数据库。目标中间件版本为 MySQL 8.4 / Redis 7；如使用其他版本，需单独验证兼容性。
2. **仅对全新空库**人工导入 [`base.sql`](sail-server/src/main/resources/db/base.sql)。当前脚本包含 28 张表、基础角色、权限、配置和默认本地存储。`spring.sql.init.mode=never`，应用启动不执行建表或迁移。已有库按[数据库升级](#数据库升级)处理，不重复导入基础脚本。
3. 检查 [`application-local.yaml`](sail-server/src/main/resources/application-local.yaml) 的 MySQL、Redis、初始管理员、AES 主密钥和本地存储目录，使其与自己的开发环境一致。开发配置直接读取 YAML，不依赖 `.env` 或 `SAIL_*` 变量。初始管理员配置仅在账号不存在时生效，不会覆盖已有密码。
4. 构建并启动后端：

```powershell
.\mvnw.ps1 '-Dmaven.test.skip=true' clean package
.\scripts\run-local.ps1
```

`mvnw.ps1` 首次运行会下载并校验 Maven 3.9.11。启动脚本要求 PowerShell 7，以工程根目录为工作目录，并设置 JVM 时区为 `Asia/Shanghai`。需要只为本次启动指定 JDK 时，可使用 `.\scripts\run-local.ps1 -JavaHome 'C:\path\to\jdk-21'`。Windows 下重新打包前，先停止正在使用目标 JAR 的进程。

5. 新开一个终端，安装依赖并启动前端：

```powershell
if (-not (Test-Path sail-ui/.env.local)) { Copy-Item sail-ui/.env.example sail-ui/.env.local }
Set-Location sail-ui
corepack pnpm install --frozen-lockfile
corepack pnpm dev --host localhost
```

前端示例配置的 `SAIL_BACKEND_URL` 指向 `http://127.0.0.1:8081`。开发服务默认使用 `http://localhost:5173`，将 `/api` 和 `/ws` 代理到该地址。若已有本地 `.env.local`，先核对代理目标，不要覆盖自己的配置；Vite 改动环境文件后需重启。实际访问域名或端口也须与后端 `sail.websocket.allowed-origins` 一致。

后端公共配置在 local 环境默认监听 **8081**，生产 profile 配置为 **8080**。若本机同时运行 IDE 后端和旧 JAR，应先确认前端代理指向哪个进程，避免两个进程使用同一数据库执行后台任务。浏览器入口为 `http://localhost:5173/auth/sign-in`；开发管理员用户名和首次密码以本地 YAML 的 `sail.bootstrap` 为准。

启动后可检查 `http://127.0.0.1:8081/actuator/health`，正常时返回 `UP`。登录接口是 `POST /api/system/auth/login`；后续接口使用 `Authorization: Bearer <登录返回的 tokenValue>`。普通 JSON 接口返回 `code`、`message`、`data` 和 `traceId`，列表从 `page=1` 开始分页。消息 WebSocket 使用登录后获取的短期一次性票据，断线后重新获取票据并查询收件箱。完整请求字段与权限见[接口文档](docs/api.md)；其中历史示例使用的 `baseUrl` 应按本地实际监听端口设置。

## 配置与数据

| 环境 | 配置文件 | 使用方式 |
| --- | --- | --- |
| local | `sail-server/src/main/resources/application-local.yaml` | 默认 profile；开发连接、主密钥、初始管理员和本地目录直接配置 |
| test | `sail-server/src/test/resources/application-test.yaml` | 测试显式启用；测试资源不进入生产 JAR |
| prod | `sail-server/src/main/resources/application-prod.yaml` | 显式启用；连接、密钥、存储目录、WebSocket 来源等需独立填写，当前空值不能直接用于生产 |

`sail.security.master-key` 用于支付和存储敏感配置的 AES-256-GCM 加密。已有密文时不得直接替换主密钥；应连同数据库与文件一起备份，并在迁移时保持可解密性。用户密码保存在 `sys_user_credential`，使用 BCrypt 完整哈希和独立盐列，与 AES 主密钥无关。

本地文件默认写入 `./storage/<存储配置编号>/`。每种存储类型可配置多套实例，但新上传只使用一个选中的默认实例；切换默认值不移动历史文件，历史文件仍按原配置读取。普通文件默认私有，只有显式标记公开的对象可匿名读取；上传单文件上限为 20 MiB。文件归属和消息附件访问在后端校验。

所有表的逻辑删除约定是 `deleted=0` 表示有效，删除时写入该行 `id`。数据库是业务事实来源；Redis 用于会话、临时票据和通知等。系统配置中的站点信息和登录策略与支付、存储秘密分开管理。

## 数据库升级

[`base.sql`](sail-server/src/main/resources/db/base.sql) 只用于全新空库，已包含当前结构；已有库先备份，再检查实际表结构和已执行的变更，只应用缺失的增量脚本一次。MySQL DDL 不可按普通事务整体回滚，失败后应检查当前结构，不能盲目重跑整个文件。

| 脚本 | 适用的已有库场景 |
| --- | --- |
| [`20261101-role-remark.sql`](scripts/migrations/20261101-role-remark.sql) | `sys_role` 缺少 `remark` 字段 |
| [`20261101-admin-management.sql`](scripts/migrations/20261101-admin-management.sql) | 旧版缺少字典类型、审计扩展或菜单图标等管理结构 |
| [`20261101-message-center.sql`](scripts/migrations/20261101-message-center.sql) | 尚未创建消息发布任务、接收快照等消息中心结构 |
| [`20261101-message-preview.sql`](scripts/migrations/20261101-message-preview.sql) | **已经完成**消息中心升级，但尚无主动弹窗公告字段 |

旧版若仍在 `sys_user` 保存密码材料，还需先完成凭据分表迁移；当前仓库未提供该迁移脚本，不能仅靠上述文件完成这类旧库升级。以上脚本也不是可对任意旧库依次无条件执行的升级器。迁移前须备份数据库、存储目录和 AES 主密钥；升级失败后核对表结构及数据，再决定恢复或补齐步骤。

## 开发与验证

后端完整测试、打包及前端静态检查可分别执行：

```powershell
# 工程根目录；执行后端测试与打包
.\mvnw.ps1 test
.\mvnw.ps1 '-DskipTests' package

# sail-ui 目录
corepack pnpm lint
corepack pnpm test:run
corepack pnpm build
```

`-Dmaven.test.skip=true` 会跳过测试代码编译与执行，适合仅需快速构建产物的场景；`-DskipTests` 仍编译测试代码。前端 `build` 包含 `vue-tsc` 类型检查和 Vite 构建。上述命令是复验入口，本次 README 编辑没有重新执行业务测试。仓库已有自动测试与本地页面验收记录，但真实微信/支付宝商户交易、MinIO/阿里云 OSS 读写、目标版本中间件及正式生产部署仍需分别验证。

## 生产部署

首期部署一个 `sail-server` 后端进程，使用同一 HTTPS 域名提供前端静态文件、`/api/` 和 `/ws/`。构建产物是 `sail-server/target/sail-server-1.0.0.jar` 与完整的 `sail-ui/dist/`；运行服务器不需要 Maven、Node.js 或前端 `node_modules`。MySQL、Redis、Nginx、证书及进程管理由操作者人工准备。本项目尚无正式域名、服务器路径、生产连接和商户资料，不能将 local 配置或以下要求当作已完成的生产部署。

### 外部配置

生产启动时显式启用 `prod`，并通过 Spring 的 `spring.config.additional-location` 加载服务器上的独立 `application-prod.yaml`。当前仓库内的生产 YAML 留空关键值，部署前必须按实际环境填写：

| 配置 | 生产要求 |
| --- | --- |
| `spring.datasource.url/username/password` | 指向独立 MySQL 数据库和具有业务所需权限的账号；新空库人工导入 `base.sql`，已有库核对结构后迁移 |
| `spring.data.redis.host/port/username/password/database`、`sail.redis.namespace` | 指向实际 Redis，命名空间与其他环境隔离；Redis 7 保持 `sail.redis.legacy-compatible=false` |
| `sail.security.master-key` | 使用 Base64 编码的 32 字节密钥；迁移已有加密配置时保留原密钥，或先制定重加密方案 |
| `sail.bootstrap.username/password/salt` | 仅首次创建管理员时使用；生产设置独立初始口令，`salt` 可留空由 BCrypt 生成 |
| `sail.storage.local-root` | 使用持久化绝对路径，并赋予应用账号读写权限；迁移已有库时同步保留 `<存储配置编号>/<对象 key>` 目录结构 |
| `sail.websocket.allowed-origins` | 填浏览器实际访问的 HTTPS 来源；非标准端口也要匹配 |
| `server.address/port`、`logging.file.name` | 后端仅对可信代理开放；生产端口默认 8080，日志写入独立持久化路径 |

前端生产构建使用同源接口：`VITE_SERVER_API_URL` 留空、`VITE_SERVER_API_PREFIX=/api`。这些值会写入静态资源，修改后需要重新构建。`SAIL_BACKEND_URL` 只控制 Vite 开发代理，不会配置生产 Nginx。支付应用与渠道需在后台录入真实微信/支付宝商户材料、公网 HTTPS 回调地址；MinIO 和阿里云 OSS 需实际 endpoint、bucket 与凭据。缺少资料时保持对应能力未启用，不能把启动成功视为外部联调通过。

### Nginx 要点

- `/` 提供 `dist/`，前端路由刷新回退到 `index.html`；入口 HTML 避免长期缓存，带内容哈希的资源可以缓存。
- `/api/` 代理到生产后端时保留原路径，不剥离 `/api` 前缀；`/ws/` 同样保留路径，并支持 HTTP/1.1、`Upgrade`、`Connection` 及适合长连接的读取超时。
- 转发实际 `Host` 和协议、客户端地址相关请求头。WebSocket 来源白名单按浏览器看到的正式来源填写。支付平台回调路径不能被代理层额外的登录校验阻断，应用仍负责回调验签。
- 代理上传限制须覆盖应用的单文件 20 MiB、请求 21 MiB 上限；`/actuator/health` 只供内部检查。前端与支付回调使用有效 HTTPS 证书。

### 人工上线与回滚

1. 确认域名、证书、服务器目录、服务账号、MySQL、Redis 和持久化文件目录。确定是新空库还是迁移已有库。
2. 备份现有数据库、文件、AES 主密钥及上一版 JAR/前端静态文件。迁移旧环境时暂停写入和后台任务，再同步最终数据与文件，避免两边继续产生不同记录。
3. 执行前后端构建，把 JAR 和完整 `dist/` 放入新版本目录，填写并检查独立的生产配置。数据库脚本由操作者按实际结构选择，不能在已有库重导 `base.sql`。
4. 人工启动单个后端进程，检查启动日志和内部健康接口；再接入 Nginx，核对登录、菜单授权、数据范围、消息收发、文件上传预览下载和 WebSocket 重连。真实支付需单独验证下单、回调、查单、关单及退款，云存储需验证实际读写。
5. 确认后切换正式流量，保留上一版本与备份。回滚前核对旧版应用和当前数据库结构是否兼容；存在不兼容变更时先制定数据库恢复或兼容迁移。恢复旧备份会丢失备份后的写入，上传目录与主密钥不随应用版本删除。

## 来源与许可

- 后端模块拆分参考 [RuoYi Office](https://github.com/yuqing2026/ruoyi-office) 的 `master-jdk17` 提交 `ad56d726368d8c321644d47898a76d3e86f1b548`；本工程按已确认范围重新实现，原 MIT 文本保存在 `licenses/ruoyi-office-MIT.txt`。
- 前端基于 [shadcn-vue-admin](https://github.com/Whbbit1999/shadcn-vue-admin) 的 `main` 分支快照 `31a71ec84d8154486f32755a9e2ab761031a242c`。上游说明保存在 `sail-ui/README.upstream.md`，`sail-ui/LICENSE` 保留 `Copyright (c) 2024 Whbbit1999` 及 MIT 原文，分发时必须随附。
- 登录背景 GhostFibers 来自用户提供的 [Vue Bits](https://vue-bits.dev/) 实现，版权归 David Haz（2025），适用 **MIT + Commons Clause License Condition v1.0**，原文在 `licenses/vue-bits-LICENSE.md`。该许可允许作为应用的一部分使用，但限制出售、再许可或再分发组件本身，包括组件集合、模板或移植版；将本工程作为框架或模板分发前须核对授权。背景使用的 `ogl` 遵循其 npm 发布包内许可证。
- 文件预览使用 PDF.js（Apache-2.0）、Mammoth（BSD-2-Clause）、ExcelJS（MIT）、Marked（MIT）、Apache POI（Apache-2.0）及 vue-easy-lightbox（MIT）；富文本使用 Tiptap（MIT）。Lucide 图形遵循其包内许可，动态图标参考 Lucide Animated 的交互方式但未引入 React 组件。Spring、MyBatis-Plus、Sa-Token、支付及对象存储 SDK 等仍遵循各自发布包许可证；发行前按最终依赖清单核对版权、许可和 NOTICE。
