-- Sail Admin MySQL 8.4 基础脚本：仅由操作者对新建空库手工导入，不由应用自动执行。
-- 逻辑删除统一为 deleted=0；删除时 SET deleted=id，不使用布尔值或时间戳代替。
-- MySQL 不允许 CHECK 引用 AUTO_INCREMENT 列，因此该等值规则由应用删除语句保证。
-- Sail Admin 初始业务结构；用户密码由首次启动参数生成，不在脚本内提供默认密码。
SET NAMES utf8mb4;

CREATE TABLE sys_audit (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `user_id` BIGINT COMMENT '用户编号',
    `action` VARCHAR(255) COMMENT '操作名称，不包含敏感请求参数',
    `success` BOOLEAN COMMENT '操作是否成功',
    `trace_id` VARCHAR(255) COMMENT '服务端请求追踪编号',
    `operation_type` VARCHAR(32) COMMENT '操作业务类型，字典sys_operation_type',
    `request_method` VARCHAR(16) COMMENT 'HTTP请求方式，字典sys_request_method',
    `request_url` VARCHAR(512) COMMENT '请求路径，不包含认证头和查询串',
    `request_params` TEXT COMMENT '脱敏查询参数，最多8192字符并标明截断',
    `request_body` TEXT COMMENT '脱敏JSON请求正文，文件及二进制内容不记录',
    `response_body` TEXT COMMENT '脱敏业务响应快照，最多8192字符并标明截断',
    `http_status` INT COMMENT '最终HTTP响应状态码',
    `response_code` INT COMMENT '统一业务响应码，无业务响应时为空',
    `duration_ms` BIGINT COMMENT '请求处理耗时，单位毫秒',
    `error_message` TEXT COMMENT '可定位的业务或参数错误说明，不含秘密',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录与操作审计记录';

CREATE TABLE sys_config (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `config_key` VARCHAR(255) COMMENT '系统参数键',
    `config_value` VARCHAR(2000) COMMENT '系统参数值',
    `description` VARCHAR(255) COMMENT '说明',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统非敏感参数';

CREATE TABLE sys_dept (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `parent_id` BIGINT COMMENT '父节点编号，根节点为空',
    `name` VARCHAR(255) COMMENT '名称',
    `sort_order` INT COMMENT '显示排序号，升序',
    `status` INT COMMENT '部门状态：1启用0停用',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门组织树';

CREATE TABLE sys_dictionary_type (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字典类型主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间，上海时区',
    `code` VARCHAR(64) NOT NULL COMMENT '稳定字典类型编码',
    `name` VARCHAR(64) NOT NULL COMMENT '字典类型中文名称',
    `status` INT NOT NULL DEFAULT 1 COMMENT '类型状态：1启用0停用',
    `built_in` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '系统类型保护标记，编码不可修改删除',
    `remark` VARCHAR(255) COMMENT '字典类型用途说明',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0有效，删除后等于主键id',
    UNIQUE KEY uk_dictionary_type_code (code,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统字典类型';

CREATE TABLE sys_dictionary (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `color` VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT '标签色：default、success、warning、error、processing',
    `remark` VARCHAR(255) COMMENT '字典数据说明',
    `is_default` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否为此类型的默认项',
    `built_in` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '内置业务值保护标记，标签可维护',
    `type_code` VARCHAR(255) COMMENT '字典类型编码',
    `label` VARCHAR(255) COMMENT '字典展示名称',
    `value` VARCHAR(255) COMMENT '字典业务值',
    `sort_order` INT COMMENT '显示排序号，升序',
    `status` INT COMMENT '字典状态：1启用0停用',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统字典项';

CREATE TABLE sys_menu (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `parent_id` BIGINT COMMENT '父节点编号，根节点为空',
    `icon` VARCHAR(64) COMMENT 'Lucide图标名称，由菜单管理配置',
    `name` VARCHAR(255) COMMENT '名称',
    `permission` VARCHAR(255) COMMENT '接口权限标识',
    `path` VARCHAR(255) COMMENT '前端路由路径',
    `type` VARCHAR(32) COMMENT '节点类型：DIRECTORY目录、MENU菜单、BUTTON按钮',
    `sort_order` INT COMMENT '显示排序号，升序',
    `status` INT COMMENT '菜单状态：1启用0停用',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单及按钮权限';

CREATE TABLE sys_post (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `name` VARCHAR(255) COMMENT '名称',
    `code` VARCHAR(255) COMMENT '稳定业务编码',
    `status` INT COMMENT '岗位状态：1启用0停用',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位资料';

CREATE TABLE sys_role (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `name` VARCHAR(255) COMMENT '名称',
    `code` VARCHAR(255) COMMENT '稳定业务编码',
    `status` INT COMMENT '角色状态：1启用0停用',
    `data_scope` INT COMMENT '数据范围：1全部2自定义部门3本部门4本部门及下级5本人',
    `remark` VARCHAR(255) NULL COMMENT '角色备注',
    `built_in` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否内置角色，内置角色不可修改删除',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色与数据范围';

CREATE TABLE sys_user (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `username` VARCHAR(255) COMMENT '登录账号',
    `nickname` VARCHAR(255) COMMENT '用户显示名称',
    `dept_id` BIGINT COMMENT '部门编号，可为空',
    `status` INT COMMENT '账号状态：1启用0停用',
    `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until` TIMESTAMP(6) COMMENT '登录锁定截止时间，为空表示未锁定',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

CREATE TABLE sys_user_credential (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '凭据主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '凭据最近更新时间，上海时区',
    `user_id` BIGINT NOT NULL COMMENT '关联用户编号',
    `password_hash` VARCHAR(60) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '标准BCrypt完整编码，包含算法参数和盐，不存储明文',
    `password_salt` CHAR(29) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BCrypt前29位：版本、成本和每次设密独立生成的128位随机盐',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行凭据主键id',
    UNIQUE KEY uk_user_credential_active (user_id,deleted),
    UNIQUE KEY uk_user_credential_salt (password_salt),
    CONSTRAINT fk_credential_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户密码凭据，密码哈希和独立随机盐与用户资料分表存储';

CREATE TABLE file_storage_config (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `name` VARCHAR(255) COMMENT '名称',
    `type` VARCHAR(32) COMMENT '存储类型：LOCAL本地、MINIO、ALIYUN阿里云OSS',
    `endpoint` VARCHAR(255) COMMENT '对象存储服务地址，本地存储为空',
    `bucket` VARCHAR(255) COMMENT '对象存储桶名称，本地存储为空',
    `access_key_cipher` TEXT COMMENT '访问标识的AES-GCM密文',
    `secret_key_cipher` TEXT COMMENT '访问密钥的AES-GCM密文',
    `region` VARCHAR(255) COMMENT '对象存储区域，MinIO必须明确配置',
    `enabled` BOOLEAN COMMENT '是否启用，停用不影响历史数据读取',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件存储实例配置';

CREATE TABLE file_storage_default (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `config_id` BIGINT COMMENT '当前默认存储实例编号',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当前默认文件存储';

CREATE TABLE file_object (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `owner_id` BIGINT COMMENT '所属用户编号',
    `storage_id` BIGINT COMMENT '实际上传时的存储配置编号，切换默认不改变此值',
    `object_key` VARCHAR(255) COMMENT '服务端生成的对象键',
    `original_name` VARCHAR(255) COMMENT '上传时原始文件名，仅用于展示',
    `content_type` VARCHAR(255) COMMENT '检测得到的媒体类型',
    `size` BIGINT COMMENT '文件字节数',
    `public_read` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许通过公开下载接口匿名读取',
    `status` VARCHAR(32) COMMENT '文件业务状态：READY，删除使用deleted字段',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件元数据';

CREATE TABLE message_template (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `code` VARCHAR(255) COMMENT '稳定业务编码',
    `name` VARCHAR(255) COMMENT '名称',
    `title` VARCHAR(255) COMMENT '消息标题模板，支持命名变量',
    `content` TEXT COMMENT '消息正文模板，支持命名变量',
    `enabled` BOOLEAN COMMENT '是否启用，停用不影响历史数据读取',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息模板';

CREATE TABLE message_record (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `recipient_id` BIGINT COMMENT '接收用户编号',
    `sender_id` BIGINT COMMENT '真实发送用户编号，系统内部调用可为空',
    `template_code` VARCHAR(255) COMMENT '发送时使用的模板编码',
    `title` VARCHAR(255) COMMENT '消息标题',
    `content` TEXT COMMENT '消息正文',
    `read_flag` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已读',
    `read_at` TIMESTAMP(6) COMMENT '首次标记已读时间',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息收件记录';

CREATE TABLE pay_application (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `code` VARCHAR(255) COMMENT '稳定业务编码',
    `name` VARCHAR(255) COMMENT '名称',
    `enabled` BOOLEAN COMMENT '是否启用，停用不影响历史数据读取',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付业务应用';

CREATE TABLE pay_channel (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `application_id` BIGINT COMMENT '支付业务应用编号',
    `type` VARCHAR(32) COMMENT '渠道类型：WECHAT_NATIVE、ALIPAY_PRECREATE',
    `name` VARCHAR(255) COMMENT '名称',
    `app_id` VARCHAR(255) COMMENT '支付渠道应用ID',
    `merchant_id` VARCHAR(255) COMMENT '微信商户号或支付宝seller_id',
    `merchant_serial` VARCHAR(255) COMMENT '微信商户证书序列号',
    `public_key_id` VARCHAR(255) COMMENT '微信支付平台公钥ID',
    `private_key_cipher` TEXT COMMENT '商户私钥的AES-GCM密文',
    `public_key_cipher` TEXT COMMENT '支付平台公钥的AES-GCM密文',
    `api_v3_key_cipher` TEXT COMMENT '微信APIv3密钥的AES-GCM密文',
    `notify_base_url` VARCHAR(255) COMMENT '公网HTTPS回调基础地址',
    `enabled` BOOLEAN COMMENT '是否启用，停用不影响历史数据读取',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付渠道与加密凭据';

CREATE TABLE pay_notification (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `order_id` BIGINT COMMENT '支付订单编号',
    `business_no` VARCHAR(255) COMMENT '业务系统订单号，应用内幂等',
    `event_type` VARCHAR(32) COMMENT '业务事件类型：PAID或REFUNDED',
    `status` VARCHAR(32) COMMENT '投递状态：WAITING待投递、DELIVERED已完成',
    `attempts` INT NOT NULL DEFAULT 0 COMMENT '业务处理器调用次数',
    `next_attempt_at` TIMESTAMP(6) COMMENT '下次允许投递时间',
    `last_error` VARCHAR(255) COMMENT '最近错误类型或明确诊断，不包含秘密',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付业务事件投递记录';

CREATE TABLE pay_order (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `application_id` BIGINT COMMENT '支付业务应用编号',
    `channel_id` BIGINT COMMENT '支付渠道配置编号',
    `owner_id` BIGINT COMMENT '所属用户编号',
    `business_no` VARCHAR(255) COMMENT '业务系统订单号，应用内幂等',
    `order_no` VARCHAR(255) COMMENT '稳定渠道商户订单号，32位随机字符串',
    `subject` VARCHAR(255) COMMENT '交易标题',
    `amount` BIGINT COMMENT '人民币金额，单位分，必须大于0',
    `currency` VARCHAR(32) COMMENT '币种，首期固定CNY',
    `status` VARCHAR(32) COMMENT '支付状态：PENDING、PAID、CLOSED、REFUNDED',
    `qr_code` VARCHAR(2048) COMMENT '渠道返回的二维码内容',
    `channel_trade_no` VARCHAR(255) COMMENT '实际渠道支付交易流水号',
    `expires_at` TIMESTAMP(6) COMMENT '订单固定付款截止时间',
    `paid_at` TIMESTAMP(6) COMMENT '本地确认渠道支付成功的时间',
    `last_error` VARCHAR(255) COMMENT '最近错误类型或明确诊断，不包含秘密',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='扫码支付订单';

CREATE TABLE pay_refund (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近更新时间，上海时区',
    `order_id` BIGINT COMMENT '支付订单编号',
    `refund_no` VARCHAR(255) COMMENT '稳定商户退款单号，重试复用',
    `reason` VARCHAR(255) COMMENT '整单退款原因',
    `amount` BIGINT COMMENT '人民币金额，单位分，必须大于0',
    `status` VARCHAR(32) COMMENT '退款状态：PROCESSING处理中、SUCCESS成功',
    `channel_refund_no` VARCHAR(255) COMMENT '实际渠道退款流水，渠道未提供则为空',
    `last_error` VARCHAR(255) COMMENT '最近错误类型或明确诊断，不包含秘密',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='整单退款记录';

CREATE TABLE sys_user_role (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `user_id` BIGINT NOT NULL COMMENT '用户编号',
    `role_id` BIGINT NOT NULL COMMENT '角色编号',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id',
    UNIQUE KEY uk_sys_user_role_active (user_id,role_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色授权关系';
CREATE TABLE sys_role_menu (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `role_id` BIGINT NOT NULL COMMENT '角色编号',
    `menu_id` BIGINT NOT NULL COMMENT '菜单编号',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id',
    UNIQUE KEY uk_sys_role_menu_active (role_id,menu_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色菜单授权关系';
CREATE TABLE sys_role_dept (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `role_id` BIGINT NOT NULL COMMENT '角色编号',
    `dept_id` BIGINT NOT NULL COMMENT '部门编号',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id',
    UNIQUE KEY uk_sys_role_dept_active (role_id,dept_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色自定义部门数据范围';
CREATE TABLE sys_user_post (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `user_id` BIGINT NOT NULL COMMENT '用户编号',
    `post_id` BIGINT NOT NULL COMMENT '岗位编号',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id',
    UNIQUE KEY uk_sys_user_post_active (user_id,post_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户岗位关系';
CREATE TABLE job_lease (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键编号',
    `name` VARCHAR(80) NOT NULL COMMENT '任务唯一名称',
    `owner` VARCHAR(64) COMMENT '当前租约持有者随机标识',
    `lease_until` TIMESTAMP(6) NOT NULL COMMENT '租约有效截止时间',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除，删除后等于本行主键id',
    UNIQUE KEY uk_job_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台任务执行租约';
CREATE UNIQUE INDEX uk_user_username ON sys_user(`username`,deleted);
CREATE UNIQUE INDEX uk_role_code ON sys_role(`code`,deleted);
CREATE UNIQUE INDEX uk_post_code ON sys_post(`code`,deleted);
CREATE UNIQUE INDEX uk_dictionary_value ON sys_dictionary(`type_code`,`value`,deleted);
CREATE UNIQUE INDEX uk_config_key ON sys_config(`config_key`,deleted);
CREATE UNIQUE INDEX uk_template_code ON message_template(`code`,deleted);
CREATE INDEX idx_message_recipient_read ON message_record(recipient_id,read_flag,id);
CREATE INDEX idx_file_owner_status ON file_object(owner_id,status,id);
CREATE INDEX idx_file_storage ON file_object(storage_id);
CREATE UNIQUE INDEX uk_pay_app_code ON pay_application(code);
CREATE UNIQUE INDEX uk_pay_business_no ON pay_order(application_id,business_no);
CREATE UNIQUE INDEX uk_pay_order_no ON pay_order(order_no);
CREATE INDEX idx_pay_pending ON pay_order(status,updated_at);
CREATE UNIQUE INDEX uk_refund_order ON pay_refund(order_id);
CREATE UNIQUE INDEX uk_refund_no ON pay_refund(refund_no);
CREATE INDEX idx_refund_pending ON pay_refund(status,updated_at);
CREATE UNIQUE INDEX uk_pay_event ON pay_notification(order_id,event_type);
CREATE INDEX idx_pay_notify_pending ON pay_notification(status,next_attempt_at);
CREATE INDEX idx_audit_created ON sys_audit(created_at);
INSERT INTO sys_role(id,name,code,status,data_scope,built_in) VALUES (1,'超级管理员','SUPER_ADMIN',1,1,TRUE);
INSERT INTO file_storage_config(id,name,type,enabled) VALUES (1,'默认本地磁盘','LOCAL',TRUE);
INSERT INTO file_storage_default(id,config_id) VALUES (1,1);
INSERT INTO job_lease(name,lease_until) VALUES ('pay-compensation','2000-01-01 00:00:00');
INSERT INTO sys_config(config_key,config_value,description) VALUES ('website.title','Sail Admin','网站名称'),('login.max_attempts','5','连续登录失败锁定阈值'),('login.lock_minutes','15','账号锁定分钟数');
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (1,'system:user:query','system:user:query','BUTTON',1,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (2,'system:user:write','system:user:write','BUTTON',2,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (3,'system:role:query','system:role:query','BUTTON',3,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (4,'system:role:write','system:role:write','BUTTON',4,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (5,'system:menu:query','system:menu:query','BUTTON',5,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (6,'system:dept:query','system:dept:query','BUTTON',6,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (7,'system:post:query','system:post:query','BUTTON',7,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (8,'system:dictionary:query','system:dictionary:query','BUTTON',8,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (9,'system:config:query','system:config:query','BUTTON',9,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (10,'system:audit:query','system:audit:query','BUTTON',10,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (11,'file:upload','file:upload','BUTTON',11,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (12,'file:query','file:query','BUTTON',12,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (13,'file:delete','file:delete','BUTTON',13,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (14,'file:manage','file:manage','BUTTON',14,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (15,'message:template:query','message:template:query','BUTTON',15,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (16,'message:template:write','message:template:write','BUTTON',16,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (17,'message:send','message:send','BUTTON',17,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (18,'pay:query','pay:query','BUTTON',18,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (19,'pay:create','pay:create','BUTTON',19,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (20,'pay:manage','pay:manage','BUTTON',20,1);
INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (21,'pay:refund','pay:refund','BUTTON',21,1);

ALTER TABLE sys_role ADD CONSTRAINT ck_role_scope CHECK(data_scope BETWEEN 1 AND 5);
ALTER TABLE pay_order ADD CONSTRAINT ck_order_amount CHECK(amount > 0);
ALTER TABLE pay_refund ADD CONSTRAINT ck_refund_amount CHECK(amount > 0);
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_user FOREIGN KEY(user_id) REFERENCES sys_user(id);
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_role FOREIGN KEY(role_id) REFERENCES sys_role(id);
ALTER TABLE sys_role_menu ADD CONSTRAINT fk_role_menu_role FOREIGN KEY(role_id) REFERENCES sys_role(id);
ALTER TABLE sys_role_menu ADD CONSTRAINT fk_role_menu_menu FOREIGN KEY(menu_id) REFERENCES sys_menu(id);
ALTER TABLE sys_role_dept ADD CONSTRAINT fk_role_dept_role FOREIGN KEY(role_id) REFERENCES sys_role(id);
ALTER TABLE sys_role_dept ADD CONSTRAINT fk_role_dept_dept FOREIGN KEY(dept_id) REFERENCES sys_dept(id);
ALTER TABLE sys_user_post ADD CONSTRAINT fk_user_post_user FOREIGN KEY(user_id) REFERENCES sys_user(id);
ALTER TABLE sys_user_post ADD CONSTRAINT fk_user_post_post FOREIGN KEY(post_id) REFERENCES sys_post(id);
ALTER TABLE file_storage_default ADD CONSTRAINT fk_storage_default FOREIGN KEY(config_id) REFERENCES file_storage_config(id);
ALTER TABLE file_object ADD CONSTRAINT fk_file_storage FOREIGN KEY(storage_id) REFERENCES file_storage_config(id);
ALTER TABLE pay_channel ADD CONSTRAINT fk_channel_app FOREIGN KEY(application_id) REFERENCES pay_application(id);
ALTER TABLE pay_order ADD CONSTRAINT fk_order_app FOREIGN KEY(application_id) REFERENCES pay_application(id);
ALTER TABLE pay_order ADD CONSTRAINT fk_order_channel FOREIGN KEY(channel_id) REFERENCES pay_channel(id);
ALTER TABLE pay_refund ADD CONSTRAINT fk_refund_order FOREIGN KEY(order_id) REFERENCES pay_order(id);
ALTER TABLE pay_notification ADD CONSTRAINT fk_notify_order FOREIGN KEY(order_id) REFERENCES pay_order(id);

INSERT INTO sys_menu(id,name,permission,type,sort_order,status) VALUES (22,'消息记录','message:query','BUTTON',22,1);

-- 内置导航目录及页面，按钮权限编号保持不变。
INSERT INTO sys_menu(name,path,icon,type,sort_order,status) SELECT '工作空间','/workspace','house','DIRECTORY',1,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/workspace' AND deleted=0);
INSERT INTO sys_menu(name,path,icon,type,sort_order,status) SELECT '系统管理','/system','settings-2','DIRECTORY',2,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system' AND deleted=0);
INSERT INTO sys_menu(name,path,icon,type,sort_order,status) SELECT '文件与消息','/content','folder','DIRECTORY',3,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/content' AND deleted=0);
INSERT INTO sys_menu(name,path,icon,type,sort_order,status) SELECT '支付管理','/pay','credit-card','DIRECTORY',4,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/pay' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/workspace' AND deleted=0),'工作台','/dashboard','house',NULL,'MENU',1,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/dashboard' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'用户管理','/system/users','users','system:user:query','MENU',2,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/users' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'角色授权','/system/roles','shield-check','system:role:query','MENU',3,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/roles' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'菜单权限','/system/menus','list-tree','system:menu:query','MENU',4,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/menus' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'部门管理','/system/depts','building-2','system:dept:query','MENU',5,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/depts' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'岗位管理','/system/posts','briefcase-business','system:post:query','MENU',6,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/posts' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'数据字典','/system/dictionaries','book-open','system:dictionary:query','MENU',7,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/dictionaries' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'系统配置','/system/configs','settings-2','system:config:query','MENU',8,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/configs' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/system' AND deleted=0),'操作审计','/system/audit','clipboard-list','system:audit:query','MENU',9,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/system/audit' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/content' AND deleted=0),'文件管理','/files','folder','file:query','MENU',10,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/files' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/content' AND deleted=0),'消息模板','/messages/templates','file-text','message:template:query','MENU',11,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/messages/templates' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/content' AND deleted=0),'消息发送','/messages/send','send','message:send','MENU',12,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/messages/send' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/content' AND deleted=0),'消息记录','/messages/history','mail','message:query','MENU',13,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/messages/history' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/pay' AND deleted=0),'支付订单','/pay/orders','credit-card','pay:query','MENU',14,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/pay/orders' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/pay' AND deleted=0),'支付应用','/pay/applications','blocks','pay:query','MENU',15,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/pay/applications' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/pay' AND deleted=0),'支付渠道','/pay/channels','wallet',NULL,'MENU',16,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/pay/channels' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/pay' AND deleted=0),'退款记录','/pay/refunds','receipt','pay:manage','MENU',17,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/pay/refunds' AND deleted=0);
INSERT INTO sys_menu(parent_id,name,path,icon,permission,type,sort_order,status) SELECT (SELECT id FROM sys_menu WHERE path='/pay' AND deleted=0),'业务通知','/pay/notifications','bell','pay:manage','MENU',18,1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/pay/notifications' AND deleted=0);
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/users' AND deleted=0) WHERE type='BUTTON' AND permission='system:user:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/users' AND deleted=0) WHERE type='BUTTON' AND permission='system:user:write' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/roles' AND deleted=0) WHERE type='BUTTON' AND permission='system:role:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/roles' AND deleted=0) WHERE type='BUTTON' AND permission='system:role:write' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/menus' AND deleted=0) WHERE type='BUTTON' AND permission='system:menu:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/depts' AND deleted=0) WHERE type='BUTTON' AND permission='system:dept:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/posts' AND deleted=0) WHERE type='BUTTON' AND permission='system:post:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/dictionaries' AND deleted=0) WHERE type='BUTTON' AND permission='system:dictionary:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/configs' AND deleted=0) WHERE type='BUTTON' AND permission='system:config:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/system/audit' AND deleted=0) WHERE type='BUTTON' AND permission='system:audit:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/files' AND deleted=0) WHERE type='BUTTON' AND permission='file:upload' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/files' AND deleted=0) WHERE type='BUTTON' AND permission='file:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/files' AND deleted=0) WHERE type='BUTTON' AND permission='file:delete' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/files' AND deleted=0) WHERE type='BUTTON' AND permission='file:manage' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/messages/templates' AND deleted=0) WHERE type='BUTTON' AND permission='message:template:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/messages/templates' AND deleted=0) WHERE type='BUTTON' AND permission='message:template:write' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/messages/send' AND deleted=0) WHERE type='BUTTON' AND permission='message:send' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/pay/orders' AND deleted=0) WHERE type='BUTTON' AND permission='pay:query' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/pay/orders' AND deleted=0) WHERE type='BUTTON' AND permission='pay:create' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/pay/orders' AND deleted=0) WHERE type='BUTTON' AND permission='pay:manage' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/pay/refunds' AND deleted=0) WHERE type='BUTTON' AND permission='pay:refund' AND parent_id IS NULL AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) menu_parent WHERE path='/messages/history' AND deleted=0) WHERE type='BUTTON' AND permission='message:query' AND parent_id IS NULL AND deleted=0;
-- 可维护中文标签的系统字典，内置业务值保持稳定。
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'sys_status','启停状态',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='sys_status' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_status','启用','1',0,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_status' AND value='1' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_status','停用','0',1,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_status' AND value='0' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'sys_result','操作结果',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='sys_result' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_result','成功','true',0,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_result' AND value='true' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_result','失败','false',1,1,'error',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_result' AND value='false' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'sys_request_method','请求方式',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='sys_request_method' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','GET','GET',0,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='GET' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','POST','POST',1,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='POST' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','PUT','PUT',2,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='PUT' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','PATCH','PATCH',3,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='PATCH' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','DELETE','DELETE',4,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='DELETE' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','HEAD','HEAD',5,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='HEAD' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_request_method','OPTIONS','OPTIONS',6,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_request_method' AND value='OPTIONS' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'sys_operation_type','操作类型',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='sys_operation_type' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','新增','INSERT',0,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='INSERT' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','修改','UPDATE',1,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='UPDATE' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','删除','DELETE',2,1,'error',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='DELETE' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','查询','QUERY',3,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='QUERY' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','授权','GRANT',4,1,'warning',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='GRANT' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','登录','LOGIN',5,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='LOGIN' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','退出登录','LOGOUT',6,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='LOGOUT' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','上传','UPLOAD',7,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='UPLOAD' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','密码维护','PASSWORD',8,1,'warning',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='PASSWORD' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','强制下线','FORCE_LOGOUT',9,1,'error',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='FORCE_LOGOUT' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_operation_type','其他','OTHER',10,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_operation_type' AND value='OTHER' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'sys_menu_type','菜单类型',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='sys_menu_type' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_menu_type','目录','DIRECTORY',0,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_menu_type' AND value='DIRECTORY' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_menu_type','菜单','MENU',1,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_menu_type' AND value='MENU' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_menu_type','按钮','BUTTON',2,1,'warning',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_menu_type' AND value='BUTTON' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'sys_data_scope','数据范围',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='sys_data_scope' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_data_scope','全部数据','1',0,1,'warning',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_data_scope' AND value='1' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_data_scope','自定义部门','2',1,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_data_scope' AND value='2' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_data_scope','本部门','3',2,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_data_scope' AND value='3' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_data_scope','本部门及下级','4',3,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_data_scope' AND value='4' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'sys_data_scope','仅本人','5',4,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='sys_data_scope' AND value='5' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'file_storage_type','文件存储类型',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='file_storage_type' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'file_storage_type','本地磁盘','LOCAL',0,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='file_storage_type' AND value='LOCAL' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'file_storage_type','MinIO','MINIO',1,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='file_storage_type' AND value='MINIO' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'file_storage_type','阿里云 OSS','ALIYUN',2,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='file_storage_type' AND value='ALIYUN' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'pay_order_status','支付订单状态',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='pay_order_status' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'pay_order_status','待支付','PENDING',0,1,'warning',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='pay_order_status' AND value='PENDING' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'pay_order_status','已支付','PAID',1,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='pay_order_status' AND value='PAID' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'pay_order_status','已关闭','CLOSED',2,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='pay_order_status' AND value='CLOSED' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'pay_order_status','已退款','REFUNDED',3,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='pay_order_status' AND value='REFUNDED' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'pay_refund_status','退款状态',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='pay_refund_status' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'pay_refund_status','处理中','PROCESSING',0,1,'processing',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='pay_refund_status' AND value='PROCESSING' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'pay_refund_status','成功','SUCCESS',1,1,'success',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='pay_refund_status' AND value='SUCCESS' AND deleted=0);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) SELECT 'message_read_status','消息阅读状态',1,TRUE,'系统组件展示与筛选' WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary_type WHERE code='message_read_status' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'message_read_status','未读','false',0,1,'warning',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='message_read_status' AND value='false' AND deleted=0);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) SELECT 'message_read_status','已读','true',1,1,'default',FALSE,TRUE WHERE NOT EXISTS (SELECT 1 FROM sys_dictionary WHERE type_code='message_read_status' AND value='true' AND deleted=0);
INSERT INTO sys_config(config_key,config_value,description) SELECT 'website.logo','','站点标志图片地址' WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='website.logo' AND deleted=0);

-- 演示组织及角色目录：不创建演示账号，不自动授予任何现有用户权限。
INSERT INTO sys_dept(id,parent_id,name,sort_order,status) VALUES
(1,NULL,'示例企业',1,1),(2,1,'综合管理部',1,1),(3,1,'技术研发部',2,1),(4,1,'业务运营部',3,1),(5,3,'平台开发组',1,1),(6,3,'质量保障组',2,1);
INSERT INTO sys_post(id,name,code,status) VALUES
(1,'部门负责人','DEPT_MANAGER',1),(2,'研发工程师','ENGINEER',1),(3,'运营专员','OPERATOR',1),(4,'审计专员','AUDITOR',1);
INSERT INTO sys_role(id,name,code,status,data_scope,remark,built_in) VALUES
(2,'部门负责人','DEPT_MANAGER',1,4,'示例角色，管理本部门及下级数据',FALSE),
(3,'业务运营','BUSINESS_OPERATOR',1,3,'示例角色，处理本部门业务',FALSE),
(4,'审计查看','AUDIT_VIEWER',1,5,'示例角色，只读操作审计',FALSE);
INSERT INTO sys_role_menu(role_id,menu_id) VALUES (2,1),(2,6),(2,7),(2,12),(3,12),(3,18),(4,10);
CREATE INDEX idx_audit_trace ON sys_audit(trace_id);
CREATE INDEX idx_audit_type_time ON sys_audit(operation_type,created_at);

-- 消息中心增量升级；执行前备份，仅执行一次，不重导 base.sql。
CREATE TABLE message_task (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息发布任务主键',
 sender_id BIGINT NOT NULL COMMENT '真实发布用户编号',
 request_key VARCHAR(64) NOT NULL COMMENT '客户端创建幂等键，同一发送人唯一',
 version INT NOT NULL DEFAULT 0 COMMENT '编辑乐观版本，发布确认与预览必须一致',
 title VARCHAR(200) NOT NULL COMMENT '消息标题，草稿允许空白',
 content MEDIUMTEXT NOT NULL COMMENT '服务端白名单净化的富文本内容快照',
 summary VARCHAR(500) COMMENT '正文纯文本摘要',
 category VARCHAR(32) NOT NULL COMMENT '消息分类：NOTICE通知、BUSINESS业务、SYSTEM系统',
 priority VARCHAR(32) NOT NULL COMMENT '重要程度：NORMAL普通、IMPORTANT重要、URGENT紧急',
 require_confirmation BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否要求接收人显式确认',
 pinned BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否在接收箱置顶',
 action_path VARCHAR(500) COMMENT '允许的系统内部业务页面路径',
 template_code VARCHAR(64) COMMENT '使用的模板编码，直接编写时为空',
 audience_json MEDIUMTEXT NOT NULL COMMENT '接收范围规则快照，用户部门角色岗位并集后排除',
 snapshot_hash VARCHAR(64) COMMENT '发布时接收人及内容版本指纹',
 status VARCHAR(32) NOT NULL COMMENT '发布生命周期状态，与个人阅读状态分离',
 scheduled_at TIMESTAMP(6) COMMENT '计划发送时间，上海时区',
 expires_at TIMESTAMP(6) COMMENT '内容有效期截止时间，上海时区',
 published_at TIMESTAMP(6) COMMENT '接收范围冻结及确认发布时间',
 withdrawn_at TIMESTAMP(6) COMMENT '撤回时间，不清除历史阅读事实',
 attempts INT NOT NULL DEFAULT 0 COMMENT '后台失败重试次数',
 next_attempt_at TIMESTAMP(6) COMMENT '后台下次可投递时间',
 last_error VARCHAR(1000) COMMENT '最近投递失败或结束原因',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间，上海时区',
 deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效，删除后等于本行id',
 UNIQUE KEY uk_message_task_request(sender_id,request_key,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息内容及发布任务';
CREATE TABLE message_recipient (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '接收人快照主键',
 task_id BIGINT NOT NULL COMMENT '发布任务编号',
 user_id BIGINT NOT NULL COMMENT '发布时冻结的真实接收人编号',
 username VARCHAR(64) NOT NULL COMMENT '接收人账号快照',
 nickname VARCHAR(100) COMMENT '接收人名称快照',
 dept_name VARCHAR(100) COMMENT '接收人部门快照，无部门为空',
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：PENDING待发送DELIVERED成功SKIPPED跳过FAILED失败',
 error_message VARCHAR(1000) COMMENT '投递失败或跳过的具体原因',
 delivered_at TIMESTAMP(6) COMMENT '收件记录实际持久化时间',
 notified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已完成实时变化事件通知，离线仍可补读',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间，上海时区',
 deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效，删除后等于本行id',
 UNIQUE KEY uk_message_recipient(task_id,user_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息接收快照及可靠投递事件';
CREATE TABLE message_attachment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息附件关联主键',
 task_id BIGINT NOT NULL COMMENT '消息发布任务编号',
 file_id BIGINT NOT NULL COMMENT '原文件模块对象编号',
 name VARCHAR(200) NOT NULL COMMENT '附件名称快照',
 content_type VARCHAR(200) NOT NULL COMMENT '附件内容类型快照',
 file_size BIGINT NOT NULL COMMENT '附件大小，字节',
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间，上海时区',
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间，上海时区',
 deleted BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效，删除后等于本行id',
 UNIQUE KEY uk_message_attachment(task_id,file_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息附件快照及访问授权引用';
ALTER TABLE message_template MODIFY COLUMN content MEDIUMTEXT COMMENT '消息模板富文本正文，变量仅作文本替换';
ALTER TABLE message_record ADD COLUMN task_id BIGINT COMMENT '新发布任务编号，旧消息保持空值';
ALTER TABLE message_record ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE COMMENT '接收人个人归档标记，不改变发布和阅读事实';
ALTER TABLE message_record ADD COLUMN confirmed_at TIMESTAMP(6) COMMENT '用户明确确认时间，打开消息不视为确认';
CREATE UNIQUE INDEX uk_message_record_task_user ON message_record(task_id,recipient_id,deleted);
CREATE INDEX idx_message_task_schedule ON message_task(status,next_attempt_at,scheduled_at);
CREATE INDEX idx_message_task_sender ON message_task(sender_id,created_at);
CREATE INDEX idx_message_recipient_delivery ON message_recipient(task_id,status,id);
CREATE INDEX idx_message_recipient_event ON message_recipient(notified,id);
CREATE INDEX idx_message_attachment_file ON message_attachment(file_id);
INSERT INTO sys_config(config_key,config_value,description) VALUES
 ('message.max_recipients','20000','单次发布最大接收人数，1至100000'),
 ('message.schedule_days','90','定时发布最远天数，1至365'),
 ('message.max_attachments','10','单条消息附件数量上限，1至20'),
 ('message.recall_minutes','1440','发布后可撤回分钟数，1至43200'),
 ('message.reminder_enabled','1','是否启用顶部新消息提醒，0关闭1开启');

-- 独立消息目录复用原页面和权限编号，保留既有角色分配。
INSERT INTO sys_menu(name,path,icon,type,sort_order,status) SELECT '消息管理','/messages/manage','mail','DIRECTORY',4,1 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE path='/messages/manage' AND deleted=0);
UPDATE sys_menu SET name='文件管理' WHERE path='/content' AND deleted=0;
UPDATE sys_menu SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) p WHERE path='/messages/manage' AND deleted=0) WHERE path IN ('/messages/send','/messages/history','/messages/templates') AND deleted=0;
UPDATE sys_menu SET name='发布消息' WHERE path='/messages/send' AND deleted=0;
UPDATE sys_menu SET name='发布记录' WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'向全体用户发送','message:send:all','BUTTON',20,1 FROM sys_menu WHERE path='/messages/send' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'定时发布消息','message:schedule','BUTTON',21,1 FROM sys_menu WHERE path='/messages/send' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'查询全部发布','message:query:all','BUTTON',22,1 FROM sys_menu WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'查看接收回执','message:receipt:query','BUTTON',23,1 FROM sys_menu WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'导出接收回执','message:receipt:export','BUTTON',24,1 FROM sys_menu WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'取消消息发布','message:cancel','BUTTON',25,1 FROM sys_menu WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'撤回已发消息','message:withdraw','BUTTON',26,1 FROM sys_menu WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'重试失败消息','message:retry','BUTTON',27,1 FROM sys_menu WHERE path='/messages/history' AND deleted=0;
INSERT INTO sys_menu(parent_id,name,permission,type,sort_order,status) SELECT id,'删除消息模板','message:template:delete','BUTTON',28,1 FROM sys_menu WHERE path='/messages/templates' AND deleted=0;

INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) VALUES ('message_delivery_status','消息投递状态',1,TRUE,'消息中心内置业务字典');
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_delivery_status','待投递','PENDING',0,1,'processing',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_delivery_status','已投递','DELIVERED',1,1,'success',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_delivery_status','已跳过','SKIPPED',2,1,'default',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_delivery_status','投递失败','FAILED',3,1,'error',FALSE,TRUE);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) VALUES ('message_task_status','消息发布状态',1,TRUE,'消息中心内置业务字典');
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','草稿','DRAFT',0,1,'default',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','待定时发送','SCHEDULED',1,1,'processing',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','排队中','QUEUED',2,1,'processing',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','发送中','SENDING',3,1,'processing',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','已发送','SENT',4,1,'success',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','部分失败','PARTIAL_FAILED',5,1,'warning',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','发送失败','FAILED',6,1,'error',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','已取消','CANCELLED',7,1,'default',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_task_status','已撤回','WITHDRAWN',8,1,'default',FALSE,TRUE);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) VALUES ('message_priority','消息重要程度',1,TRUE,'消息中心内置业务字典');
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_priority','普通','NORMAL',0,1,'default',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_priority','重要','IMPORTANT',1,1,'warning',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_priority','紧急','URGENT',2,1,'error',FALSE,TRUE);
INSERT INTO sys_dictionary_type(code,name,status,built_in,remark) VALUES ('message_category','消息分类',1,TRUE,'消息中心内置业务字典');
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_category','通知公告','NOTICE',0,1,'processing',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_category','业务消息','BUSINESS',1,1,'success',FALSE,TRUE);
INSERT INTO sys_dictionary(type_code,label,value,sort_order,status,color,is_default,built_in) VALUES ('message_category','系统消息','SYSTEM',2,1,'default',FALSE,TRUE);

-- 仅供已经完成 message-center 升级的已有库执行一次，保留所有原消息。
ALTER TABLE message_task ADD COLUMN display_mode VARCHAR(16) NOT NULL DEFAULT 'INBOX' COMMENT '展示方式：INBOX收件箱、POPUP主动弹窗公告';
ALTER TABLE message_record ADD COLUMN popup_seen_at TIMESTAMP(6) NULL COMMENT '弹窗公告首次成功展示时间，不替代阅读和主动确认';
CREATE INDEX idx_message_popup_pending ON message_record(recipient_id,popup_seen_at,archived,deleted);

