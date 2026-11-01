-- 仅供已经完成 message-center 升级的已有库执行一次，保留所有原消息。
ALTER TABLE message_task ADD COLUMN display_mode VARCHAR(16) NOT NULL DEFAULT 'INBOX' COMMENT '展示方式：INBOX收件箱、POPUP主动弹窗公告';
ALTER TABLE message_record ADD COLUMN popup_seen_at TIMESTAMP(6) NULL COMMENT '弹窗公告首次成功展示时间，不替代阅读和主动确认';
CREATE INDEX idx_message_popup_pending ON message_record(recipient_id,popup_seen_at,archived,deleted);
