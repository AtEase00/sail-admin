-- 合并工作流入口，保留菜单 ID、权限标识和角色授权关系；可重复执行。
START TRANSACTION;

UPDATE sys_menu
SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) destination WHERE path='/messages/history' AND deleted=0)
WHERE parent_id IN (SELECT id FROM (SELECT id,path,deleted FROM sys_menu) old_entry WHERE path='/messages/send' AND deleted=0)
  AND deleted=0
  AND EXISTS (SELECT id FROM (SELECT id,path,deleted FROM sys_menu) destination WHERE path='/messages/history' AND deleted=0);

UPDATE sys_menu
SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) destination WHERE path='/messages/history' AND deleted=0),
    type='BUTTON', path=NULL, name='发布消息'
WHERE path='/messages/send' AND deleted=0
  AND EXISTS (SELECT id FROM (SELECT id,path,deleted FROM sys_menu) destination WHERE path='/messages/history' AND deleted=0);

UPDATE sys_menu
SET parent_id=(SELECT id FROM (SELECT id,path,deleted FROM sys_menu) destination WHERE path='/system/jobs' AND deleted=0),
    type='BUTTON', path=NULL
WHERE path='/system/job-executions' AND deleted=0
  AND EXISTS (SELECT id FROM (SELECT id,path,deleted FROM sys_menu) destination WHERE path='/system/jobs' AND deleted=0);

COMMIT;
