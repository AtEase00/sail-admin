# Sail Admin

JDK 21 / Spring Boot 4 的企业后台 Java 基础工程。以 RuoYi Office 的模块拆分方式为参考，围绕 RBAC、站内消息、文件、PC 扫码支付重新实现，采用 Controller → Service → Mapper 三层结构，单体进程部署。

## 首期范围

| 模块 | 已编写的能力 |
| --- | --- |
| system | 登录与锁定、会话下线、用户、角色、菜单/按钮、部门、岗位、五类数据范围、字典、系统参数、登录/操作审计 |
| message | 富文本与私有附件、模板、草稿、组织范围及排除名单、定时发布、持久化分批投递、回执导出、撤回重试、个人收件箱与确认归档、实时提醒 |
| file | 上传下载、文件归属校验、显式公开下载、本地磁盘/MinIO/阿里云 OSS、多套配置按类型分组、切换默认存储 |
| pay | 微信 Native、支付宝当面付、二维码内容、业务单号幂等、主动查单、关单、整单退款、回调验签、定时补偿、持久化业务通知 |

文件默认保存到本地 `./storage/<存储配置编号>/`。可分别创建多套 MinIO 和阿里云 OSS，选择一个启用配置作为新上传的默认存储；历史文件继续按原配置读取。

支付金额单位为人民币分，创建订单要求 `pay:create` 权限。具体业务接入时，应由业务服务按自己的订单计算应付金额，再调用支付能力。首期提供全额退款；没有钱包、分账或部分退款。支付事件由业务模块实现 `PaymentEventHandler` 消费，未接入时通知保留等待状态。

前端位于 `sail-ui`，基于 shadcn-vue-admin 源码对接真实接口，包含登录、权限导航、系统管理、消息、文件及扫码支付操作。通用任务管理平台、多租户、邮件短信和 MFA 不在本次确认范围内。`sail-gateway` 仅保留设计位置，不引入 Spring Cloud。

## 工程结构

```text
sail-admin/
├── sail-dependencies/                 # 第三方和内部模块 BOM
├── sail-common/                       # core、web、test
├── sail-framework/                    # web、security、mybatis、redis、log、oss
├── sail-modules/
│   ├── sail-system/                   # api + server
│   ├── sail-file/                     # api + server
│   ├── sail-message/                  # api + server
│   └── sail-pay/                      # api + server
├── sail-server/                       # 单体装配、环境配置和 db/base.sql
├── sail-ui/                           # shadcn-vue-admin 前端及真实接口对接
├── sail-gateway/                      # 设计位置
├── docs/                              # 架构、接口、来源审查和交付状态
├── licenses/                          # 上游原始许可
├── scripts/run-local.ps1              # 人工启动脚本
└── mvnw.ps1                           # 固定 Maven 3.9.11
```

依赖基线：Boot 4.0.9、MyBatis-Plus Boot 4 Starter 3.5.18、Sa-Token Boot 4 Starter 1.46.0、MySQL 8.4、Redis 7。Spring Framework、Jackson、HikariCP 等跟随 Boot BOM；详细声明见 `sail-dependencies/pom.xml`，插件版本由根 POM 管理。

## 本地配置

默认启用开发环境 `local`，连接信息直接写在 `sail-server/src/main/resources/application-local.yaml`：MySQL `127.0.0.1:3306/sail_admin`、root / 123456；Redis `127.0.0.1:6379`、密码 123456、数据库 0。无需 `.env` 或 `SAIL_ADMIN_USERNAME` 等变量。未配置微信、支付宝或云存储时不会在启动阶段连接它们。

1. 人工准备 JDK 21、PowerShell 7、MySQL 8.4、Redis 7 和独立 `sail_admin` 数据库。
2. 手工导入 `sail-server/src/main/resources/db/base.sql`，仅适用于新建空库。28 张表和全部字段均有中文备注；`deleted=0` 表示有效，删除写入本行主键 `id`。旧版已有库使用专用凭据迁移脚本，不要重复导入基础 SQL，详见本地配置文档。
3. 开发管理员账号、密码和随机 BCrypt 盐直接配置于 `application-local.yaml` 的 `sail.bootstrap`；AES 主密钥配置于 `sail.security.master-key`。开发初始密码为 `123456`，密码至少 6 字符且 UTF-8 不超过 72 字节。配置仅首次建号使用，不覆盖已有管理员。
4. IDE 选择 JDK 21，直接启动默认开发环境；VM 参数设置 `-Duser.timezone=Asia/Shanghai`。生产显式启用 `prod`，填写独立的 `application-prod.yaml`；自动化测试使用测试资源中的 `application-test.yaml`。
5. 数据库初始化设为 `spring.sql.init.mode=never`，不再自动运行 Flyway。备份主密钥后妥善保存，不可随意更换。

本地构建与启动命令（PowerShell 7）：

```powershell
# 在工程根目录构建；此命令跳过测试代码的编译与执行。
.\mvnw.ps1 '-Dmaven.test.skip=true' clean package

# 读取开发 YAML 启动已构建 JAR；JAVA_HOME 应指向 JDK 21。
.\scripts\run-local.ps1
```

单体入口为 `sail.server.SailApplication`，默认端口 8080。鉴权头为 `Authorization: Bearer <登录返回的 tokenValue>`；登录地址为 `POST /api/system/auth/login`，健康检查为 `/actuator/health`。

用户资料表 `sys_user` 不包含密码或盐。密码材料保存于 `sys_user_credential`：使用标准 BCrypt 完整哈希和独立盐列。开发初始管理员使用 YAML 中的随机专属盐，普通账号、改密和重置仍每次生成新盐。`sail.security.master-key` 用于第三方配置的 AES-256-GCM 加密，与用户密码盐无关。

## 文档

- [源码架构审查](docs/source-review.md)：上游目录、依赖、可参考点与迁移差异。
- [架构与扩展](docs/architecture.md)：模块职责、自动配置、权限边界、微服务拆分。
- [本地配置](docs/local-setup.md)：人工配置、秘密要求、回滚与外部资料。
- [HTTP 与 WebSocket 接口](docs/api.md)：权限、请求字段、curl 和接入顺序。
- [交付与验证状态](docs/delivery-status.md)：源码范围和未执行的验收项目。
- [前端接入](docs/frontend-integration.md)：源码来源、页面接口、代理与人工运行步骤。
- [消息中心验收](docs/message-center-acceptance-20261101.md)：消息改造、增量升级、权限与真实工作流验证。
- [公告与文件预览验收](docs/message-preview-acceptance-20261101.md)：主动弹窗、遮罩修复、文件格式范围、图标与标志上传。
- [第三方声明](THIRD_PARTY_NOTICES.md)：上游快照及许可边界。

本轮按“先能启动起来”的要求完成后端跳过测试打包、前端类型检查与构建，并连接本机 MySQL 8.0.12、Redis 3.0.504 启动。使用用户手工导入的 `sail_admin`；登录、目录查询及消息 WebSocket 已通过启动检查，未运行测试套件。访问 `http://localhost:5173`，账号 `admin`，首次密码见 `application-local.yaml` 的 `sail.bootstrap.password`。旧 Redis 兼容仅在 local 环境显式启用，生产基线仍为 MySQL 8.4 / Redis 7。真实支付和云存储尚未联调，详见交付状态。


## 消息中心更新（2026-11-01）

消息管理包含发布消息、发布记录和消息模板；我的消息放在个人菜单与顶部提醒入口。消息配置在系统配置「消息中心」维护。已有库增量脚本为 `scripts/migrations/20261101-message-center.sql`，新空库仍使用 `base.sql`，二者不要重复导入。

本次后端 28 项集成测试、前端 69 项测试及 lint/build 通过，真实本地 MySQL/Redis 与浏览器验证记录见 [消息中心验收](docs/message-center-acceptance-20261101.md)。此更新取代上文早期“未运行测试”的阶段说明；真实支付与云存储联调不在本次消息改造验收内。
