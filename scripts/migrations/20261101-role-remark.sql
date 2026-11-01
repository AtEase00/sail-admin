-- 已导入 base.sql 的数据库执行一次；新库已包含此字段，无须重复执行。
ALTER TABLE sys_role ADD COLUMN remark VARCHAR(255) NULL COMMENT '角色备注' AFTER data_scope;
