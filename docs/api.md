# HTTP 与 WebSocket 接口

## 通用约定

本地服务地址 `http://127.0.0.1:8080`。下面 curl 可导入 Apifox；建立 `baseUrl`、`token` 等环境变量，路径里的资源编号必须使用实际接口返回值，不能把示例编号作为已存在的业务数据。`{{...}}` 是待填写的 Apifox 变量，所有密码和商户资料由操作者填写。

JSON 接口返回 `{"code":0,"message":"成功","data":...,"traceId":"..."}`；失败使用实际 HTTP 状态和对应非零 code。文件下载、微信/支付宝回调响应遵循各自协议。分页参数 `page` 从 1 开始，默认 `size=20`、上限 200；分页 data 为 `total/page/size/records`。请求/响应携带 `X-Trace-Id` 便于关联日志。

除登录、渠道回调和显式公开文件外，所有 `/api/**` 都需要 `Authorization: Bearer {{token}}`。超级管理员拥有全部功能权限；标记“超级管理员”的操作还校验 `SUPER_ADMIN` 角色，只有同名按钮权限并不足以执行。

## 登录与账号

```bash
curl --request POST '{{baseUrl}}/api/system/auth/login' --header 'Content-Type: application/json' --data-raw '{"username":"admin","password":"{{adminPassword}}"}'
curl --request GET '{{baseUrl}}/api/system/auth/me' --header 'Authorization: Bearer {{token}}'
curl --request GET '{{baseUrl}}/api/system/auth/menus' --header 'Authorization: Bearer {{token}}'
curl --request PUT '{{baseUrl}}/api/system/auth/password' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"oldPassword":"{{oldPassword}}","newPassword":"{{newPassword}}"}'
curl --request POST '{{baseUrl}}/api/system/auth/logout' --header 'Authorization: Bearer {{token}}'
```

登录返回 `tokenName/tokenValue/timeout`，将 `tokenValue` 保存为环境变量 token。修改密码和重置密码后该账号全部会话失效；首次密码由环境配置创建。初始密码允许 `123456`，新密码至少 6 个字符，且 UTF-8 不超过 72 字节。

## RBAC 与系统资料

| 资源（前缀 `/api/system`） | 方法/路径 | 权限与语义 |
| --- | --- | --- |
| 用户 | `GET /users`、`GET /users/{id}` | `system:user:query`，执行数据范围；列表可传 keyword |
| 用户 | `POST /users` | `system:user:write`，服务层还要求超级管理员 |
| 用户 | `PUT /users/{id}` | `system:user:write`；调整部门需超级管理员 |
| 用户 | `DELETE /users/{id}` | `system:user:write` 且超级管理员；停用、撤销会话并逻辑删除用户及授权，deleted 写入各行主键 id，保留历史编号 |
| 密码 | `PUT /users/{id}/password` | `system:user:write` 且超级管理员 |
| 角色关系 | `GET/PUT /users/{id}/roles` | GET 需 user:query，PUT 需 role:write 且超级管理员；PUT 全量替换 |
| 岗位关系 | `GET/PUT /users/{id}/posts` | GET 需 user:query，PUT 需 user:write；执行目标用户数据范围 |
| 会话 | `GET /users/{id}/sessions`、`DELETE /audit/sessions/{userId}` | 超级管理员；会话详情不返回令牌，DELETE 强制全部下线 |
| 角色 | `GET /roles`；`POST /roles`、`PUT/DELETE /roles/{id}` | GET 需 `system:role:query`；写需超级管理员；内置角色不可修改 |
| 角色菜单 | `GET/PUT /roles/{id}/menus` | GET 需 role:query；PUT 需超级管理员，全量替换 |
| 角色范围 | `GET/PUT /roles/{id}/depts` | 超级管理员；dataScope=2 的自定义部门集合 |
| 菜单 | `GET /menus`；`POST /menus`、`PUT/DELETE /menus/{id}` | GET 需 `system:menu:query`；写需超级管理员 |
| 部门 | `GET /depts`；`POST /depts`、`PUT/DELETE /depts/{id}` | GET 需 `system:dept:query`；写需超级管理员 |
| 岗位 | `GET /posts`；`POST /posts`、`PUT/DELETE /posts/{id}` | GET 需 `system:post:query`；写需超级管理员 |
| 字典 | `GET /dictionaries`；`POST /dictionaries`、`PUT/DELETE /dictionaries/{id}` | GET 需 `system:dictionary:query`；写需超级管理员 |
| 系统参数 | `GET /configs`；`POST /configs`、`PUT /configs/{id}` | GET 需 `system:config:query`；写需超级管理员；无删除接口 |
| 审计 | `GET /audit?page=1&size=20` | `system:audit:query`，只读 |

用户/组织/角色/菜单/字典的 `status` 均为 1 启用、0 停用。部门/菜单根节点使用 `parentId:null`；菜单目录返回平铺数据，由客户端按 parentId 构树。当前登录菜单会补齐已授权菜单的启用祖先。

dataScope：1 全部，2 自定义部门，3 本部门，4 本部门及下级，5 本人。有效角色取并集；未分配任何可见范围则查询不到用户记录。分配 ids 传空数组表示清空，单次最多 500 个编号。内置管理员必须保留内置超级管理员角色。

```bash
curl --request GET '{{baseUrl}}/api/system/users?page=1&size=20&keyword=' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/system/depts' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"parentId":null,"name":"运营部","sortOrder":10,"status":1}'
curl --request POST '{{baseUrl}}/api/system/users' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"username":"operator","password":"{{operatorPassword}}","nickname":"运营人员","deptId":{{deptId}},"status":1}'
curl --request POST '{{baseUrl}}/api/system/roles' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"name":"运营角色","code":"OPERATOR","status":1,"dataScope":3}'
curl --request PUT '{{baseUrl}}/api/system/users/{{userId}}/roles' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"ids":[{{roleId}}]}'
curl --request PUT '{{baseUrl}}/api/system/roles/{{roleId}}/menus' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"ids":[{{menuId}}]}'
curl --request POST '{{baseUrl}}/api/system/menus' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"parentId":null,"name":"用户查询","permission":"system:user:query","path":null,"type":"BUTTON","sortOrder":10,"status":1}'
curl --request POST '{{baseUrl}}/api/system/posts' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"name":"运营专员","code":"OPERATOR","status":1}'
curl --request POST '{{baseUrl}}/api/system/dictionaries' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"typeCode":"business_priority","label":"普通","value":"NORMAL","sortOrder":10,"status":1}'
curl --request GET '{{baseUrl}}/api/system/configs' --header 'Authorization: Bearer {{token}}'
curl --request PUT '{{baseUrl}}/api/system/configs/{{configId}}' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"configKey":"website.title","configValue":"Sail Admin","description":"网站名称"}'
```

创建接口 data 返回新编号；更新请求除用户外与创建字段一致。用户更新只接收 `nickname/deptId/status`；重置密码只接收 `password`；用户岗位和角色部门分配均为 `{"ids":[实际编号]}`。

菜单类型仅 `DIRECTORY/MENU/BUTTON`，按钮必须给出 permission。系统参数只支持 `website.title`、`website.logo`、`login.max_attempts`、`login.lock_minutes`，登录策略值是 1～60 的整数字符串；支付/存储秘密不得写入系统参数表。

## 文件与多实例存储

`GET /api/file/objects/{id}/preview` 为旧版 Office 文本预览，沿用当前账号的文件读取权限。消息内使用 `GET /api/messages/tasks/{taskId}/attachments/{fileId}/preview`，校验发送人或有效收件关系及撤回/过期状态。图片、PDF、DOCX、XLSX、Markdown 等由前端在鉴权下载后读取真实内容，格式范围见 [预览验收](message-preview-acceptance-20261101.md)。

`POST /api/file/website-logo` 使用 multipart 的 `file` 字段，仅超级管理员可用，返回 `{id,url}`。校验真实栅格图片、最大 2 MiB，并作为公开文件保存。将返回 url 写入系统配置 `website.logo` 后统一生效；当前使用的标志禁止直接删除。公开栅格图使用真实图片 Content-Type 和 inline，其余下载保持附件。

存储管理全部要求超级管理员。`GET /api/file/storages` 返回 `LOCAL/MINIO/ALIYUN` 三组，每行含 id、配置资料、selected 和凭据已配置标志；无明文秘密。一个类型可以建多套配置，全局只选一个默认实例。

```bash
curl --request GET '{{baseUrl}}/api/file/storages' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/file/storages' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"name":"本地磁盘","type":"LOCAL","endpoint":null,"bucket":null,"accessKey":null,"secretKey":null,"region":null,"enabled":true}'
curl --request POST '{{baseUrl}}/api/file/storages' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"name":"MinIO 主存储","type":"MINIO","endpoint":"{{minioEndpoint}}","bucket":"{{minioBucket}}","accessKey":"{{minioAccessKey}}","secretKey":"{{minioSecretKey}}","region":"{{minioRegion}}","enabled":true}'
curl --request POST '{{baseUrl}}/api/file/storages' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"name":"阿里云附件存储","type":"ALIYUN","endpoint":"{{ossEndpoint}}","bucket":"{{ossBucket}}","accessKey":"{{ossAccessKey}}","secretKey":"{{ossSecretKey}}","region":null,"enabled":true}'
curl --request PUT '{{baseUrl}}/api/file/storages/{{storageId}}/default' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/file/objects?publicRead=false' --header 'Authorization: Bearer {{token}}' --form 'file=@{{filePath}}'
curl --request GET '{{baseUrl}}/api/file/objects?page=1&size=20' --header 'Authorization: Bearer {{token}}'
curl --request GET '{{baseUrl}}/api/file/objects/{{fileId}}/download' --header 'Authorization: Bearer {{token}}' --output 'downloaded-file'
curl --request DELETE '{{baseUrl}}/api/file/objects/{{fileId}}' --header 'Authorization: Bearer {{token}}'
```

重复提交云存储创建请求并给不同名称/实际配置，即可维护多套实例。`PUT /storages/{id}` 使用创建时相同字段，秘密传 null/空表示保留原值；停用将 enabled 改为 false。当前默认实例须先切换才能停用/删除。已有文件引用的配置不能变更类型、endpoint、bucket、region，也不能删除；可更新访问凭据。切换默认值不迁移已有文件。

上传需 `file:upload`，元数据详情/列表需 `file:query`，删除需 `file:delete`；`file:manage` 可访问其他用户文件，下载仍需登录。`GET /api/file/objects/{id}` 查元数据。匿名地址 `GET /api/file/public/{id}` 只对显式 publicRead=true 的对象可用。

文件上限 20 MiB，允许 png/jpg/jpeg/webp/pdf/txt/zip/docx/xlsx/pptx，并用内容检测限制类型。返回附件流，禁止浏览器嗅探类型。本地存储根目录统一由 `SAIL_STORAGE_ROOT` 配置，每个 LOCAL 实例按自身编号分目录，接口不接收任意服务器磁盘路径。

## 站内消息

消息发布采用「草稿 → 预览实际收件人数 → 确认发布 → 持久化分批投递」流程。发布记录按任务分页，个人收件箱按接收记录分页，所有搜索条件由后端过滤。原 `/send`、`/history` 保留兼容旧业务调用；新管理页面使用 `/tasks`，历史接收记录继续展示。

| 方法/路径（前缀 `/api/messages`） | 权限与行为 |
| --- | --- |
| `GET /settings` | 登录，消息业务配置，不含服务器秘密 |
| `GET /audience/users` | `message:send`，keyword/deptId/page/size 分页且应用数据权限 |
| `GET /audience/options`、`POST /audience/selected` | `message:send`，组织选项及已有人员回显，仍校验数据权限 |
| `GET /tasks` | `message:query`，本人任务；`message:query:all` 可查看全部 |
| `GET /tasks/{id}` | `message:query` 或 `message:send`，另校验任务归属 |
| `POST /tasks`、`PUT /tasks/{id}`、`DELETE /tasks/{id}` | `message:send`，只允许本人草稿/可重新编辑的取消任务 |
| `POST /tasks/{id}/preview`、`POST /tasks/{id}/publish` | `message:send`；定时发布另需 `message:schedule` |
| `POST /tasks/{id}/cancel`、`withdraw`、`retry` | 分别 `message:cancel`、`message:withdraw`、`message:retry`，同时校验归属和状态 |
| `GET /tasks/{id}/receipts`、`receipts/export` | `message:receipt:query`、`message:receipt:export`，分别分页/流式 CSV |
| `GET /mine`、`GET /mine/{id}`、`GET /unread-count` | 登录，固定当前接收人；详情记录首次阅读 |
| `PUT /read`、`read-all`、`mine/{id}/confirm`、`archive` | 登录，仅本人记录；阅读与显式确认分别记录 |
| `GET /tasks/{id}/attachments/{fileId}` | 校验当前有效收件关系或发送人归属；撤回/过期后接收人不可读取 |
| `GET /templates/page`、`GET /templates` | `message:template:query`，新页面使用分页接口 |
| `GET /templates/available` | `message:send`，仅启用模板 |
| `POST /templates`、`PUT /templates/{id}` | `message:template:write` |
| `DELETE /templates/{id}` | `message:template:delete`，逻辑删除不影响已发快照 |
| `POST /templates/{id}/preview` | `message:send` 或 `message:template:query`，服务端转义变量 |
| `POST /socket-ticket` | 登录，一次性 WebSocket 票据 |

草稿 JSON 字段：`requestKey`（客户端新建时生成唯一键）、`version`（初始 0，后续使用返回版本）、`title`、`content`（受限富文本）、`category`（NOTICE/BUSINESS/SYSTEM）、`priority`（NORMAL/IMPORTANT/URGENT）、`requireConfirmation`、`pinned`、`scheduledAt`、`expiresAt`、`actionPath`、`audience`、`attachmentIds`、`templateCode`、`displayMode`（INBOX 消息通知、POPUP 弹窗公告；旧调用未传时使用 INBOX）。日期时间固定 `yyyy-MM-dd HH:mm:ss`，可空。关联入口只接受已登记内部业务路由，不接受外部跳转。

`audience` 包含 `userIds/deptIds/roleIds/postIds/excludedIds` 数组，以及 `includeChildren/all` 布尔值。用户、部门、岗位和角色取并集，排除名单最后生效；最终与当前发送人的 RBAC 数据范围求交。发送全体用户还需 `message:send:all` 和全局数据范围。预览返回 `count/sample/version/fingerprint`，发布提交其 `version/fingerprint`；人员或版本发生变化返回冲突，必须重新预览。发布后固定收件人的账号、姓名与部门快照，不跟随组织变化。

`GET /api/messages/announcements` 向已登录账号返回最多 10 条有效、未归档、未展示的 POPUP 公告，紧急优先。`PUT /api/messages/mine/{id}/presented` 记录当前收件人的首次成功展示时间，重复调用不更新时间；已读不代替已展示，已展示也不代替主动确认。撤回或过期后禁止记录新展示及读取正文。

任务查询支持 `keyword/status/category/priority/createdFrom/createdTo/page/size`。个人消息额外支持 `folder=all|unread|important|archived` 和 `status=true|false`（已读/未读）。回执支持 `keyword/receiptStatus/page/size`；状态可为 PENDING/DELIVERED/SKIPPED/FAILED/READ/UNREAD/CONFIRMED/UNCONFIRMED，导出沿用相同筛选。`PUT /archive` 接收 `{"ids":[消息记录编号],"archived":true}`，恢复使用 false；`PUT /read` 接收 `{"ids":[消息记录编号]}`。

正文经 JSoup 与 DOMPurify 双重净化。正文图片仅引用消息附件 `data-file-id`，不持久化外部图片地址或 Blob URL；添加文件需 `file:upload`，接收人下载无需文件管理权限。被有效消息引用的文件不可删除。模板使用 `${变量名}` 文本变量，缺少值明确报错，不执行表达式。

默认每条最多 20,000 人、10 个附件、90 天内定时发布、发布后 1,440 分钟内撤回，均在系统配置「消息中心」维护。调度每 1.5 秒处理最多 20 个任务，每任务每批 500 人；数据库行锁和唯一键避免并发重复投递，数据库异常最多自动尝试 3 次并退避。失败回执可人工重试，成功人员不重复投递；通知标记持久化，Redis 恢复后重试提醒。停用/删除的接收账号与过期任务标为跳过。取消仅允许尚未开始投递的排队/定时任务；已投递后使用撤回，保留回执。

WebSocket 连接 `ws://127.0.0.1:8080/ws/messages?ticket=<返回票据>`，生产使用 wss。票据 30 秒内单次消费，绑定申请它的有效登录令牌。Origin 使用当前环境 YAML 的白名单。收到 `{"type":"MESSAGE_CHANGED"}` 后补查未读数及列表；提醒只通知数据变化，正文仍需鉴权读取。客户端重连重新申请票据并补查。重复查看已读消息不会重复推送，打开的详情会随撤回提醒或有效期到期重新读取状态。

已有库备份后执行 `scripts/migrations/20261101-message-center.sql`，该脚本只用于未执行本次消息升级的库，不能重复运行。新建空库使用 `base.sql`，不要对已有库重新导入。

## 支付配置

先创建应用，再在该应用下创建微信或支付宝渠道。配置的启停只限制新订单，历史订单继续用原渠道查单、退款。已有交易的渠道禁止变更所属应用、类型、appId、merchantId，商户更换应新建渠道。

```bash
curl --request POST '{{baseUrl}}/api/pay/applications' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"code":"business_order","name":"业务订单","enabled":true}'
curl --request POST '{{baseUrl}}/api/pay/channels' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"applicationId":{{payApplicationId}},"type":"WECHAT_NATIVE","name":"微信 Native","appId":"{{wechatAppId}}","merchantId":"{{wechatMerchantId}}","merchantSerial":"{{wechatMerchantSerial}}","publicKeyId":"{{wechatPublicKeyId}}","privateKey":"{{wechatMerchantPrivateKeyPem}}","publicKey":"{{wechatPlatformPublicKeyPem}}","apiV3Key":"{{wechatApiV3Key}}","notifyBaseUrl":"{{notifyBaseUrl}}","enabled":true}'
curl --request POST '{{baseUrl}}/api/pay/channels' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"applicationId":{{payApplicationId}},"type":"ALIPAY_PRECREATE","name":"支付宝当面付","appId":"{{alipayAppId}}","merchantId":"{{alipaySellerId}}","merchantSerial":null,"publicKeyId":null,"privateKey":"{{alipayPrivateKeyPem}}","publicKey":"{{alipayPublicKeyPem}}","apiV3Key":null,"notifyBaseUrl":"{{notifyBaseUrl}}","enabled":true}'
curl --request GET '{{baseUrl}}/api/pay/channels' --header 'Authorization: Bearer {{token}}'
```

应用查询 `GET /applications` 需 `pay:query`；`GET /channels/available` 需 `pay:create`，仅返回启用应用下启用渠道的 id/applicationId/name/type，不含商户凭据；创建/更新应用、查询全部渠道及创建/更新渠道均需超级管理员。应用更新为 `PUT /applications/{id}`，渠道更新为 `PUT /channels/{id}`，字段与创建一致；应用 code 不能更改。秘密更新传空保留原值；创建时必须提供真实密钥。

`notifyBaseUrl` 必须是实际公网 HTTPS 基础地址，不带查询参数或 fragment；渠道自动追加：

- 微信：`/api/pay/callback/wechat/{channelId}`，官方 JSON 原文及签名头；成功返回 HTTP 204。
- 支付宝：`/api/pay/callback/alipay/{channelId}`，官方 form-urlencoded 通知；成功返回纯文本 `success`。

回调不要求后台登录，但必须通过 SDK 验签和商户校验，再主动查单核实交易。不要手写无签名 curl 冒充有效回调。私钥在 JSON 中需正确转义换行；环境变量插入 PEM 时同样需要保持合法 JSON。支付宝使用正式网关，当前没有沙箱开关。

## 二维码、查单、关单与退款

```bash
curl --request POST '{{baseUrl}}/api/pay/orders' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"applicationId":{{payApplicationId}},"channelId":{{channelId}},"businessNo":"{{businessOrderNo}}","subject":"业务订单支付","amount":{{amountInCents}},"expiresMinutes":15}'
curl --request GET '{{baseUrl}}/api/pay/orders/{{payOrderId}}' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/pay/orders/{{payOrderId}}/sync' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/pay/orders/{{payOrderId}}/resubmit' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/pay/orders/{{payOrderId}}/close' --header 'Authorization: Bearer {{token}}'
curl --request POST '{{baseUrl}}/api/pay/orders/{{payOrderId}}/refund' --header 'Authorization: Bearer {{token}}' --header 'Content-Type: application/json' --data-raw '{"reason":"用户申请整单退款"}'
curl --request GET '{{baseUrl}}/api/pay/refunds?page=1&size=20' --header 'Authorization: Bearer {{token}}'
curl --request GET '{{baseUrl}}/api/pay/notifications?page=1&size=20' --header 'Authorization: Bearer {{token}}'
```

创建和重新下单需 `pay:create`；列表 `GET /orders`、详情和 sync 需 `pay:query`；close、退款列表和通知列表需 `pay:manage`；refund 需 `pay:refund`。同时检查归属，跨用户操作需 `pay:manage`。配置和管理权限应只授予可信的管理/业务操作方，不能将允许提交金额的创建接口直接开放给普通消费者。

amount 是 1～100000000 的整数分，expiresMinutes 为 1～120。businessNo 在应用内唯一，重试必须保持原金额、渠道、操作者和标题；不一致返回 409。首次网络失败仍可能已经保存订单，可按列表中 businessNo 查回记录，再使用 sync/resubmit；不要生成新的业务单号重扣。

订单返回 `qrCode` 为渠道二维码文本，前端绘制二维码后轮询本地订单状态；同步接口发起真实渠道查单。状态：PENDING 待付、PAID 已付、CLOSED 已关闭、REFUNDED 已全退。resubmit 只接受未过期 PENDING，保留原渠道单号和到期时间。关单竞争由查单与条件更新处理，未知/网络失败不能宣告成功。

refund 首期只做整单退款，同一订单复用一个退款号；处理中保留 PROCESSING，渠道确认成功后为 SUCCESS，订单转 REFUNDED。支付宝退款查询未返回独立渠道退款流水时 `channelRefundNo` 保持 null，不拿支付交易号替代。

支付/退款状态变更会写入 WAITING 通知。业务模块按应用 code 提供唯一 `PaymentEventHandler`，使用 eventId 幂等更新业务后才标记 DELIVERED；同一订单先投递支付事件，再投递退款事件。不存在处理器时 lastError 明确说明，并继续等待；这是接入点，不是模拟交付成功。

后台默认每 60 秒补偿，每类最多 50 条，由数据库租约协调多实例。查单/退款/业务通知异常保留诊断类型或明确说明，不会自动改用其他商户或其他支付渠道。真实商户、数据库、Redis、云存储尚未完成联调，见 [交付记录](delivery-status.md)。

## 管理能力补充（2026-11-01）

最新本地数据库与 Redis 验收已完成；真实商户和云存储联调边界仍保留。详细结果见 [界面验收](ui-acceptance-20261101.md)。

- 业务响应时间使用 `yyyy-MM-dd HH:mm:ss`，例如 `2026-11-01 14:30:25`。
- 所有请求响应包含 `X-Trace-Id`，统一 JSON 的 `traceId` 与其一致；请求日志和操作审计沿用同一值。
- `GET /api/system/audit` 支持 `keyword`（操作名/追踪号）、`requestMethod`、`operationType`、`status`、`createdFrom`、`createdTo`、`page`、`size`。新增快照字段为 `operationType/requestMethod/requestUrl/requestParams/requestBody/responseBody/httpStatus/responseCode/durationMs/errorMessage`；历史未采集字段为 null。
- `GET/PUT /api/system/configs/settings` 读取带中文编辑元数据的配置和保存键值对象。当前支持 `website.title`、`website.logo`、`login.max_attempts`、`login.lock_minutes`；写入沿用超级管理员权限。`GET /api/system/configs/website` 仅向已登录用户提供站点显示配置。
- 字典类型：`GET/POST /api/system/dictionaries/types`、`PUT/DELETE /api/system/dictionaries/types/{id}`。数据 CRUD 沿用 `/api/system/dictionaries`，列表支持 `typeCode`；数据新增字段 `color`、`remark`、`isDefault`、`builtIn`。业务用 `GET /api/system/dictionaries/options` 获取启用类型下的启用字典。
- 菜单新增可写 `icon` 字段，使用受支持的 Lucide 名称。文件存储业务接口仍为 `/api/file/storages`，前端入口合并至系统配置。


## 个人中心补充（2026-11-03）

登录用户均可调用，不要求用户管理权限。账号 ID 只从当前会话获取。

- `GET /api/system/auth/profile`：返回 `user`（与 `/auth/me` 相同的安全用户资料）、`department`（名称或 null）、`posts`（有效岗位名称数组）、`createdAt`。
- `PUT /api/system/auth/profile`：请求 `{ "nickname": "新昵称" }`，昵称必填且最长 64 字符，保存时移除两端空白；返回更新后的安全用户资料。不会更新客户端额外传入的 ID、部门、账号状态、角色或密码。操作进入审计。
- `PUT /api/system/auth/password`：沿用原密码校验与全部会话撤销行为。
- WebSocket `/ws/messages` 新增应用层心跳：客户端每 25 秒发 `{"type":"PING"}`，服务端回复 `{"type":"PONG"}`。使用已有一次性票据认证；10 秒无应答客户端重连。

## 定时任务与执行记录（2026-11-03）

统一前缀 `/api/system/jobs`，均须登录。权限完整前缀为 `system:job:`。

| 方法与路径 | 权限后缀 | 用途 |
| --- | --- | --- |
| `GET /` | `query` | 任务分页，支持 keyword/status/page/size/id |
| `GET /handlers` | `query` | 已注册处理器 code/name/description/retryable |
| `POST /preview` | `query` | 校验完整配置，返回未来最多 5 次时间（epoch 毫秒） |
| `POST /` | `create` | 新建暂停任务，返回任务 ID |
| `PUT /{id}` | `update` | 按版本更新配置，返回任务 ID |
| `PUT /{id}/status` | `control` | 启用、暂停、归档 |
| `POST /{id}/run` | `run` | 立即执行，返回持久化执行 ID |
| `GET /executions` | `log:query` | 执行分页，支持 jobId/keyword/status/source/createdFrom/createdTo/page/size |
| `GET /executions/{id}` | `log:query` | 运行详情及配置快照 |
| `POST /executions/{id}/retry` | `run` + `log:query` | 人工重试已结束 FAILED/PARTIAL，返回新执行 ID |
| `POST /executions/{id}/resolve` | `control` + `log:query` | 确认 UNKNOWN 的原进程已停止并解除阻塞 |

新增、更新、预览使用同一配置结构：

```json
{
  "name": "调度连通性检查",
  "groupName": "运维",
  "handler": "system.diagnostic",
  "parameters": "{}",
  "scheduleType": "CRON",
  "cron": "0 0/5 * * * ?",
  "intervalSeconds": null,
  "zoneId": "Asia/Shanghai",
  "misfirePolicy": "SKIP",
  "timeoutSeconds": 60,
  "maxRetries": 0,
  "retryDelaySeconds": 30,
  "retentionDays": 30,
  "version": 0
}
```

更新必须传当前 version（新建传 0），服务端成功后递增。`INTERVAL` 需传 intervalSeconds（10–2592000），为固定频率；Cron 为 Quartz 6/7 字段。参数是 JSON **字符串**，由注册处理器进一步校验，不能包含秘密。超时 5–86400 秒、重试 0–3 次、首次退避 10–3600 秒、记录保留 1–365 天。重试仅允许明确支持幂等重试的处理器。

状态变更体为 `{"version":1,"status":"ENABLED"}`，status 只接受 ENABLED/PAUSED/ARCHIVED。执行和人工重试体为 `{"version":2}`（当前任务配置版本）；任务必须启用、已同步且无未结束执行。版本冲突或任务占用返回 HTTP 409；配置或其他状态校验失败 400、无权限 403。

人工处置体为 `{"reason":"原执行进程已停止，已核对业务结果……"}`，reason 必填且最多 500 字符。只能处置仍占用任务的 UNKNOWN；原记录保留 UNKNOWN，另存处置说明和处置完成时间，操作者记录在操作审计中，不伪造成功。

任务时间字段及执行 scheduledAt/startedAt/finishedAt/heartbeatAt/createdAt 为 epoch 毫秒；未发生为 null，durationMs 为毫秒。任务返回 syncStatus/syncError，执行返回 configSnapshot（JSON 字符串）、actorName、instanceId、traceId、succeeded/failed、summary 和 parentRunId。执行来源 SCHEDULED/MANUAL/RETRY；状态与故障语义见 [运行说明](job-scheduler.md)。
