# Sail Admin 请求日志排查与部署思路

本文基于 2026-11-01 的源码、监听端口、进程命令行及现有日志整理。仅提供人工操作方案，未修改运行配置，未停止服务，未执行打包、测试或部署。

## 1. 请求日志为什么在 IDE 控制台看不到

当前同时存在两套后端进程：

| 入口 | 实际目标 | 当前进程 |
| --- | --- | --- |
| 前端开发代理 | `.env.local` 中 `SAIL_BACKEND_URL=http://127.0.0.1:8080` | JAR 后端，PID 38736，15:00 启动 |
| IDEA 运行控制台 | 公共 YAML 的 `server.port=8081` | IDE 后端，PID 6632，16:27 启动 |

因此页面操作打到 8080，不会出现在 8081 的 IDE 控制台。进程编号仅代表排查时的快照，重启后会变化。

源码 `RequestTraceFilter` 已记录请求开始与结束，包含追踪号、方法、路径、状态、耗时，以及脱敏参数、JSON 正文和统一业务响应。`WebConfiguration` 已注册该过滤器，开发配置的请求日志开关也已开启。

现有 `logs/sail-admin.log` 中已存在真实请求记录，例如：

```text
2026-11-01 16:38:28.664 INFO [traceId=489c05933ead4d4180d335cd6424475b] ... 请求完成 traceId=489c05933ead4d4180d335cd6424475b method=GET path=/api/messages/unread-count status=200 durationMs=2
```

两套后端的启动记录均显示工作目录为工程根目录，公共配置又使用相同的 `./logs/sail-admin.log`，因此日志文件会混入两个进程的记录；IDE 控制台则只展示它启动的进程。

控制台持续出现的两条消息查询来自 `MessageDeliveryService.Scheduler`，默认每 1500ms 执行一次。开发环境把 `org.springframework.jdbc.core.JdbcTemplate` 设为 DEBUG，因此空队列轮询也打印 SQL。调度线程没有 HTTP 请求上下文，其 `traceId` 为空符合当前实现。

### 本地处理建议

若以后通过 IDEA 开发，保持 IDE 的 8081，将 `sail-ui/.env.local` 中的 `SAIL_BACKEND_URL` 改为 `http://127.0.0.1:8081`，再重启前端开发服务，使代理重新加载配置。开发用的 `/api` 和 `/ws` 均读取此目标。

确认旧 JAR 不再承担请求后，再人工停止旧 JAR，避免两套后端连接同一数据库、同时执行定时任务。不要根据本文的旧 PID 直接结束进程，应先核对当时的命令行。

若需要减少刷屏，可把开发 YAML 中 `logging.level.org.springframework.jdbc.core.JdbcTemplate` 调为 INFO。请求日志使用独立的 `sail.framework.web.RequestTraceFilter` INFO 日志，不受此项影响；MyBatis Mapper 的 SQL 日志也可按模块分别设置。

## 2. 推荐部署结构

首期采用同一域名、单台应用服务器的模块化单体部署：

```text
浏览器 → HTTPS / Nginx
                 ├─ /             → 前端 dist 静态文件
                 ├─ /api/         → 127.0.0.1:8080，保留 /api 前缀
                 └─ /ws/          → 127.0.0.1:8080，保留 /ws 前缀并支持 Upgrade
                                      ├─ MySQL 8.4
                                      ├─ Redis 7
                                      └─ 本地文件目录 / MinIO / 阿里云 OSS
```

后端部署一个 `sail-server` 可执行 JAR 即可，其他 Java 子模块随它装配。当前 `sail-gateway` 不需要独立启动。服务器通过 Nginx 提供前端，不运行 Vite 开发服务或 `pnpm preview`。

建议目录如下，这是拟采用的服务器路径，不代表已创建：

| 目录 | 用途 |
| --- | --- |
| `/opt/sail-admin/releases/<版本号>/` | 每个版本的 JAR 和前端 dist |
| `/opt/sail-admin/current` | 指向当前版本，便于切换 |
| `/etc/sail-admin/application-prod.yaml` | 独立生产配置 |
| `/var/lib/sail-admin/storage/` | 持久化上传目录，不随版本替换 |
| `/var/log/sail-admin/` | 应用日志 |

首期只启一个后端进程。未来扩容时，本地磁盘文件需要改为共享存储或对象存储，并单独确认任务并发和反向代理策略。

## 3. 打包前需要准备的配置

生产显式启用 `prod`。当前 `application-prod.yaml` 的数据库、Redis 主机、主密钥、管理员初始密码、文件目录、WebSocket 来源均未填写，直接启用不能作为已配置完成的服务使用。

| 配置 | 填写原则 |
| --- | --- |
| `spring.datasource.url/username/password` | 生产库连接；使用具有所需业务权限的独立账号 |
| `spring.data.redis.host/port/username/password/database` | 实际 Redis 连接；无 ACL 用户时 username 留空 |
| `sail.redis.namespace` | 当前 prod 配置为 `sail-prod`，与开发隔离 |
| `sail.redis.legacy-compatible` | 生产 Redis 7 保持 false |
| `sail.security.master-key` | Base64 编码的 32 字节 AES 密钥；新库使用独立生产密钥，迁移已有加密配置时保持原密钥或先完成重加密 |
| `sail.bootstrap.username/password/salt` | 新库首次建管理员时使用；salt 可留空让 BCrypt 生成。已有 id=1 的管理员不会被配置覆盖或重置 |
| `sail.storage.local-root` | 填持久化绝对目录，如 `/var/lib/sail-admin/storage`，服务账号必须可读写 |
| `sail.websocket.allowed-origins` | 实际前端来源，如正式 HTTPS 域名，包含非标准端口时必须写端口，不带页面路径 |
| `server.port` / `server.address` | 建议 8080 / 127.0.0.1，供同机 Nginx 访问 |
| `logging.file.name` | 填绝对日志路径，避免受工作目录影响 |

生产显式设置 `sail.web.request-log-enabled=true`，`logging.level.sail.framework.web.RequestTraceFilter=INFO`，保留访问记录；JDBC、Mapper 的 SQL 日志平时使用 INFO，需要定位 SQL 时再按包开启 DEBUG。当前过滤器默认开关为 true，生产日志行为应以实际配置为准。

请求体及响应日志经过脱敏并限制长度，不会保存附件二进制内容；响应快照针对统一 `ApiResult`，不代表下载文件等任意响应正文都会被完整记录。业务操作审计与每次 HTTP 请求的控制台日志也有不同的记录范围。

前端推荐同源部署，构建时 `VITE_SERVER_API_URL` 留空，`VITE_SERVER_API_PREFIX=/api`。这些值在构建阶段写入静态资源，修改后需要重新构建。`SAIL_BACKEND_URL` 只影响 Vite 开发代理，不是生产服务器的接口转发配置。

## 4. 人工构建产物

构建机使用 JDK 21、Node.js >=22.15、pnpm 11.28.6。工程根目录现有 `mvnw.ps1` 固定使用 Maven 3.9.11。

在 Windows PowerShell 的工程根目录执行后端构建：

```powershell
# 跳过测试编译与执行，生成所有模块和可执行 JAR。
.\mvnw.ps1 '-Dmaven.test.skip=true' clean package
```

当前本地旧 JAR 正在运行，Windows 下重新打包前需要先人工停止该 JAR，避免文件占用。IDE 服务是否保留按实际开发需要决定。

在 `sail-ui` 目录执行前端构建：

```powershell
# 按锁文件安装依赖，再生成生产静态资源。
pnpm install --frozen-lockfile
pnpm build
```

需要上传的应用产物只有 `sail-server/target/sail-server-1.0.0.jar` 和 `sail-ui/dist/` 的完整内容。服务器只运行产物时不需要 Maven、Node.js 或前端 node_modules。

## 5. 数据库、文件和密钥一起考虑

两种场景二选一：

- **全新生产空库**：人工导入当前 `sail-server/src/main/resources/db/base.sql`；不再重复执行已包含的历史增量脚本。基础脚本带有用户要求的部门、岗位和角色模拟数据，上线前人工确认保留哪些。
- **迁移现有本地库**：导出完整结构与数据，在服务器恢复；按实际表结构确认缺失升级。不要重新导入 `base.sql`，也不要盲目把全部历史迁移再执行一遍。已有库可能涉及凭据分表、管理功能、角色备注、消息中心、主动弹窗等版本差异。

应用配置为 `spring.sql.init.mode=never`，启动不会代替人工升级数据库。

迁移已有数据时同时复制实际使用的存储根目录。目录结构必须保留 `<存储配置编号>/<对象 key>`，只迁数据库会导致附件、站点标志等记录存在但文件不可读。若本地文件已分布在不同历史工作目录，要根据实际配置与文件位置核实来源，不能假设全部都在一个 `storage` 目录。

已有云存储、支付渠道等加密配置依赖原 AES 主密钥；随机替换密钥不能解开这些历史配置。新空库可以使用独立生产密钥，迁移旧库应连同原密钥妥善迁移或先完成重加密。用户 BCrypt 密码哈希和盐在 `sys_user_credential`，不依赖 AES 主密钥。

## 6. 服务器启动及 Nginx 要点

建议 Linux 使用 systemd 管理 JAR，工作目录固定到当前版本目录，使用独立服务账号。按上述目录约定，服务的 Java 启动命令为：

```bash
java -Duser.timezone=Asia/Shanghai -jar /opt/sail-admin/current/sail-server-1.0.0.jar --spring.profiles.active=prod --spring.config.additional-location=file:/etc/sail-admin/
```

外部 `application-prod.yaml` 放在指定目录中覆盖包内生产配置；服务器重启与进程异常重启由 systemd 管理。后端已有 graceful shutdown，给服务留足正常退出时间。

Nginx 配置应覆盖以下行为：

- 前端路由刷新回退到 `index.html`；`index.html` 避免长期缓存，带内容哈希的静态资源可以长期缓存。
- `/api/` 转发保留原 URI。`proxy_pass http://127.0.0.1:8080;` 不附加用于剥离前缀的 URI，避免后端收到不含 `/api` 的路径。
- `/ws/` 支持 HTTP/1.1、`Upgrade`、`Connection`，保留路径并设置适合长连接的读取超时；关闭不必要的代理缓冲。
- 转发实际 `Host`、`X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto`，按实际需求配置后端可信代理处理；来源白名单使用浏览器访问的正式来源。
- 上传限制与应用匹配：应用单文件上限 20MB、请求上限 21MB，Nginx 请求体可设为 21m，避免代理比应用更早拒绝正常上传。
- HTTPS 证书和正式域名由操作者实际提供。后台与支付回调都走 HTTPS；支付回调 `/api/pay/callback/` 不增加会阻断支付平台的 Nginx 登录校验。
- `/actuator/health` 可用于服务器内部检查，不需要公开其他管理端点。

微信与支付宝的正式商户材料、回调基础域名，以及启用的 MinIO/OSS 配置需在后台配置并完成真实联调。没有支付资料时，先上线管理功能，保持对应支付渠道未启用；启动服务不等于支付已经可用。

## 7. 人工上线顺序与回滚

1. 确认服务器系统、正式域名、HTTPS、MySQL、Redis、目录及服务账号。
2. 备份待迁移数据库、上传文件、主密钥及现有线上版本；确定新库或迁移旧库。
3. 本地构建新版本，上传 JAR 与完整 dist，填写独立外部生产配置。
4. 首次迁移最终数据时暂停旧环境写入和后台任务，再完成最终数据库与文件同步，避免两边持续产生分叉。
5. 人工启动单个后端，查看启动日志及内部健康状态；再接入 Nginx 前端与 `/api`、`/ws` 转发。
6. 人工确认登录、菜单权限、列表过滤、操作日志追踪号、消息通知、上传预览下载及站点标志。支付需要单独完成支付、回调、查单和退款联调后启用。
7. 确认正常后切换正式流量，保留上一版本和备份。

回滚时同时考虑代码与数据库结构兼容。没有结构变更时可以切回上一版本 JAR/dist；存在不兼容结构变更时，先制定数据库恢复或兼容迁移方式。恢复旧备份会丢失备份之后的新写入，不能当成无损操作。上传目录和主密钥不随应用版本切换或删除。

本次尚未提供服务器操作系统、正式域名、部署目录、生产 MySQL/Redis 连接及支付资料，因此本文没有编造对应实际值，也没有宣称已完成服务器上线验收。
