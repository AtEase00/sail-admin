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
