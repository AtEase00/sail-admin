# 本地配置说明

开发直接读取 `sail-server/src/main/resources/application-local.yaml`，不需要 `.env` 或 `SAIL_*` 变量。MySQL 为 `127.0.0.1:3306/sail_admin`，账号 root、密码 123456；Redis 为 `127.0.0.1:6379`，密码 123456。已使用用户准备的原库，不自动建库或导入结构。

| 环境 | 配置 | 选择方式 |
| --- | --- | --- |
| 开发 | `src/main/resources/application-local.yaml` | 默认启用，也可显式 `--spring.profiles.active=local` |
| 测试 | `src/test/resources/application-test.yaml` | 测试类显式 `@ActiveProfiles("test")`，使用测试 H2 和临时 Redis |
| 生产 | `src/main/resources/application-prod.yaml` | 显式 `--spring.profiles.active=prod`，独立填写实际连接和密钥 |

以上路径相对 `sail-server`。公共 `application.yaml` 只保留共用框架参数。生产配置未提供的连接、主密钥、密码、文件目录和 WebSocket 来源留空，不继承开发值。测试配置只在测试资源中，不打进生产 JAR。

开发管理员账号、初始密码、专属随机 BCrypt 盐分别为 `sail.bootstrap.username/password/salt`。盐已随机生成并直接写入 YAML，仅首次创建管理员时使用；现有账号不覆盖。普通用户、管理员后续改密和密码重置均产生新的独立随机盐。AES 主密钥直接配置在 `sail.security.master-key`，保持原值，不参与密码加盐。

本机实际 MySQL 8.0.12、Redis 3.0.504。开发 YAML 已启用旧 Redis 兼容，生产仍使用 MySQL 8.4 / Redis 7。前端访问 `http://localhost:5173`，后端 `http://127.0.0.1:8080`。日志与进程记录在 `logs/`。

使用 JDK 21 和 PowerShell 7 启动：

```powershell
# JAVA_HOME 已指向 JDK 21 时直接执行。
./scripts/run-local.ps1
# 也可只为本次启动指定 JDK，不修改系统默认 Java。
./scripts/run-local.ps1 -JavaHome C:/Users/35620/.jdks/ms-21.0.10
```

构建使用 `./mvnw.ps1 '-Dmaven.test.skip=true' package`，JAR 为 `sail-server/target/sail-server-1.0.0.jar`。重新打包前需停止本工程后端，避免 Windows 占用 JAR。本轮不运行测试套件。新空库人工导入 `base.sql`，已有库不重复导入。开发首次密码为 `123456`，密码至少 6 字符且 UTF-8 不超过 72 字节，配置初始口令不代表重置已有口令。

## 开发控制台日志

`application-local.yaml` 已启用 `sail.web.request-log-enabled=true`，记录请求开始、结束、HTTP 方法、路径、状态和耗时，SQL 与请求共用 `traceId`。MyBatis 使用 `sail.framework.mybatis.SqlConsoleLog`，系统、文件、消息、支付四组 Mapper 开启 DEBUG，输出带 `?` 占位符的 SQL 及影响行数，不输出参数值或结果数据。请求不记录查询串、请求正文、Cookie、认证头。异常逃出过滤链时记录异常退出，不伪造容器尚未决定的最终状态。生产未开启这些开发日志。修改后需重新运行 IDE 后端服务；JAR 方式需先重新打包。

## 数据与回滚

`base.sql` 是新库基础结构，不是已有库升级脚本，不可在已有数据的库中直接重复导入。已有库升级需要按实际结构编写并审查差异脚本，尤其是关系表新增主键与逻辑删除标记、唯一键变更。此次曾提前创建并导入 `sail_admin_local`，用户说明已自行准备原库后，最终切回 `sail_admin`；额外库保留原样，未擅自删除。启动初始化会创建缺失的管理员、内置角色和菜单，登录会产生会话与审计。上线前备份数据库与文件目录；回滚代码前核对数据库前后兼容性。切换默认存储不移动历史文件；不能删除仍有有效文件引用的配置。主密钥备份与数据库备份应分开保存。

所有表的逻辑删除标记为 BIGINT：有效值 0，删除值为本行 `id`。用户、角色菜单、角色部门、用户岗位等关系清理也使用该规则。可复用目录编码的唯一键包含 `deleted`；支付订单、退款、通知等幂等唯一键保持全局约束，未提供这些记录的删除接口。MySQL 对 AUTO_INCREMENT 列的 CHECK 限制使此处不添加 `deleted=id` 检查约束，删除表达式由应用控制。

前端人工环境配置见 [前端接入](frontend-integration.md)，需要 Node.js >=22.15、pnpm 11.28.6。开发代理默认连接本地 8080，页面默认 5173；WebSocket 来源白名单与实际访问的主机名需要一致。

## 用户凭据分表升级

新库的 `base.sql` 直接创建 `sys_user_credential`，用户资料表不再保存密码材料。旧版已有库需停止后端、备份 `sys_user`，选中目标库后执行 [20261031_user_credential.sql](../sail-server/src/main/resources/db/migrations/20261031_user_credential.sql)。脚本检查旧 BCrypt 格式及盐唯一性，整批拷贝原始哈希并提取原盐，逐行校验成功后才移除用户表旧列。不重置密码，不根据未知明文重新计算盐；异常时保留原列供排查。MySQL DDL 不可事务回滚，脚本仅执行一次，失败后应检查当前状态，不能整段盲目重复执行。

本机 `sail_admin` 已完成此升级。迁移前用户数据及表结构备份位于 Git 忽略的 `logs/user-credential-before-20261031-234009.json`，含敏感密码哈希，不应上传或公开。凭据记录的审计时间表示该独立记录的建立与更新时间，不冒用资料表更新时间作为已知设密时间。

`password_hash` 保留标准 BCrypt 完整编码，`password_salt` 保存其前 29 位（算法版本、成本和 128 位随机盐）。编码本身按标准已包含盐，独立盐列用于一致性校验及唯一约束；验证直接使用标准 BCrypt，不重复拼盐。普通账号新增、改密和重置统一使用同一凭据服务，每次生成新盐；开发初始管理员专用入口接受 YAML 中的随机盐；用户与凭据共用事务，删除均遵守各自主键的逻辑删除规则。并发改密通过原哈希条件更新检测冲突。

已生成的 32 字节安全随机主密钥现保存于开发 YAML 的 `sail.security.master-key`，从旧配置迁入时保持值不变。旧 `.env` 已归档至忽略的 `logs/previous-local.env`，启动不读取它。后续存在密文时不得直接覆盖主密钥，必须先制定密文重加密方案。该主密钥不参与用户密码或盐的生成。

## 外部资料

- 微信 Native：应用 ID、商户号、商户证书序列号、商户私钥、微信支付平台公钥及公钥 ID、32 字节 APIv3 密钥、回调基础 URL。
- 支付宝当面付：应用 ID、商户 seller_id、应用 PKCS8 私钥、支付宝公钥、回调基础 URL；当前实现为 RSA2 公钥模式。
- MinIO：endpoint、bucket、region、访问密钥与秘密密钥。bucket 由操作者创建，框架不会自动创建。
- 阿里云 OSS：endpoint、bucket、访问密钥与秘密密钥。建议使用仅限目标 bucket 的 RAM 子账号。

资料缺失时明确拒绝启用对应能力，不会自动替换商户、渠道或文件存储。


### 角色备注字段升级

已导入旧版 `base.sql` 的本地库，只执行一次 `scripts/migrations/20261101-role-remark.sql`，随后从 IDE 重启后端。不要重新导入全量初始化脚本覆盖现有数据。新建库导入当前 `base.sql` 后无需重复执行此增量。本轮仅准备升级 SQL，未操作数据库。
