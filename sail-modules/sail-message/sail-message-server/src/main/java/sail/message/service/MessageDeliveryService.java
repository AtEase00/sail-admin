package sail.message.service;

import static sail.message.service.MessageCenterRepository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sail.system.api.MessageAudienceDirectory;

import java.time.LocalDateTime;
import java.util.*;

/** 数据库驱动的分批投递与通知补偿；任务行锁保证多实例和人工操作互斥。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDeliveryService {
    private final MessageCenterRepository store;
    private final MessageAudienceDirectory users;
    private final TransactionTemplate transactions;
    private final MessageSocket sockets;

    /** 每轮处理有限任务和有限收件人，宕机后从已提交批次继续。 */
    public void deliverDue() {
        var tasks =
                store.list(
                        "SELECT id FROM message_task WHERE deleted=0 AND status IN"
                            + " ('SCHEDULED','QUEUED','SENDING') AND"
                            + " next_attempt_at<=CURRENT_TIMESTAMP ORDER BY next_attempt_at,id"
                            + " LIMIT 20",
                        Map.of());
        for (var task : tasks) {
            long id = number(task, "id");
            try {
                transactions.executeWithoutResult(tx -> deliverBatch(id));
            } catch (org.springframework.dao.DataAccessException failure) {
                // 失败批次整体回滚，单独事务记录重试状态，保留完整异常和任务编号。
                log.error("消息投递失败 taskId={}", id, failure);
                transactions.executeWithoutResult(tx -> recordFailure(id, failure));
            }
        }
        notifyPending();
    }

    /** 在同一行锁事务中创建收件记录并确认快照投递状态，唯一键防止重复。 */
    private void deliverBatch(long id) {
        var task = store.task(id, true);
        if (task == null
                || !Set.of("SCHEDULED", "QUEUED", "SENDING").contains(task.get("status"))
                || time(task, "nextAttemptAt").isAfter(LocalDateTime.now())) return;
        var args = Map.<String, Object>of("id", id);
        if (time(task, "expiresAt") != null
                && !time(task, "expiresAt").isAfter(LocalDateTime.now())) {
            store.update(
                    "UPDATE message_recipient SET"
                        + " status='SKIPPED',error_message='消息已过期',updated_at=CURRENT_TIMESTAMP"
                        + " WHERE task_id=:id AND status='PENDING'",
                    args);
        } else {
            var batch =
                    store.list(
                            "SELECT id,user_id FROM message_recipient WHERE task_id=:id AND"
                                + " deleted=0 AND status='PENDING' ORDER BY id LIMIT "
                                    + MessageConstants.BATCH_SIZE,
                            args);
            var active = users.activeIds(batch.stream().map(row -> number(row, "userId")).toList());
            List<Map<String, ?>> delivered = new ArrayList<>(), skipped = new ArrayList<>();
            for (var recipient : batch) {
                long user = number(recipient, "userId");
                var item =
                        Map.<String, Object>of(
                                "task",
                                id,
                                "recipient",
                                user,
                                "sender",
                                number(task, "senderId"),
                                "id",
                                number(recipient, "id"),
                                "title",
                                task.get("title"));
                (active.contains(user) ? delivered : skipped).add(item);
            }
            // 正文仅保存一份任务快照，个人记录关联任务，避免大正文按人数重复存储。
            store.batch(
                    "INSERT INTO"
                        + " message_record(task_id,recipient_id,sender_id,title,content,read_flag)"
                        + " VALUES(:task,:recipient,:sender,:title,'',FALSE)",
                    delivered);
            store.batch(
                    "UPDATE message_recipient SET"
                        + " status='DELIVERED',delivered_at=CURRENT_TIMESTAMP,error_message=NULL,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=:id",
                    delivered);
            store.batch(
                    "UPDATE message_recipient SET"
                        + " status='SKIPPED',error_message='账号已停用或删除',updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=:id",
                    skipped);
        }
        var counts =
                store.one(
                        "SELECT SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) AS"
                            + " pending,SUM(CASE WHEN status='FAILED' THEN 1 ELSE 0 END) AS"
                            + " failed,SUM(CASE WHEN status='DELIVERED' THEN 1 ELSE 0 END) AS"
                            + " delivered FROM message_recipient WHERE task_id=:id AND deleted=0",
                        args);
        String state =
                number(counts, "pending") > 0
                        ? "SENDING"
                        : number(counts, "failed") > 0
                                ? (number(counts, "delivered") > 0 ? "PARTIAL_FAILED" : "FAILED")
                                : "SENT";
        store.update(
                "UPDATE message_task SET"
                    + " status=:state,attempts=0,next_attempt_at=CURRENT_TIMESTAMP,last_error=NULL,updated_at=CURRENT_TIMESTAMP"
                    + " WHERE id=:id",
                Map.of("id", id, "state", state));
    }

    /** 有界退避后明确失败，管理员可以只重试失败的接收人。 */
    private void recordFailure(long id, Exception failure) {
        var task = store.task(id, true);
        if (task == null || !Set.of("SCHEDULED", "QUEUED", "SENDING").contains(task.get("status")))
            return;
        int attempts = (int) number(task, "attempts") + 1;
        Map<String, Object> args = new HashMap<>();
        args.put("id", id);
        args.put("attempts", attempts);
        args.put("next", LocalDateTime.now().plusSeconds(15L * attempts));
        args.put("error", failure.getClass().getSimpleName() + "，请凭任务编号查看服务端错误日志");
        if (attempts >= MessageConstants.MAX_ATTEMPTS) {
            store.update(
                    "UPDATE message_recipient SET"
                        + " status='FAILED',error_message=:error,updated_at=CURRENT_TIMESTAMP WHERE"
                        + " task_id=:id AND status='PENDING'",
                    args);
            long delivered =
                    number(
                            store.one(
                                    "SELECT COUNT(*) AS total FROM message_recipient WHERE"
                                        + " task_id=:id AND status='DELIVERED'",
                                    args),
                            "total");
            args.put("state", delivered > 0 ? "PARTIAL_FAILED" : "FAILED");
        } else args.put("state", "QUEUED");
        store.update(
                "UPDATE message_task SET"
                    + " status=:state,attempts=:attempts,next_attempt_at=:next,last_error=:error,updated_at=CURRENT_TIMESTAMP"
                    + " WHERE id=:id",
                args);
    }

    /** 先广播再确认通知标记；崩溃可能重复提醒但不会丢失已入库消息。 */
    private void notifyPending() {
        var rows =
                store.list(
                        "SELECT id,user_id FROM message_recipient WHERE notified=FALSE AND"
                            + " status='DELIVERED' AND deleted=0 ORDER BY id LIMIT "
                                + MessageConstants.BATCH_SIZE,
                        Map.of());
        if (rows.isEmpty()) return;
        try {
            sockets.broadcast(rows.stream().map(row -> number(row, "userId")).distinct().toList());
            store.update(
                    "UPDATE message_recipient SET notified=TRUE,updated_at=CURRENT_TIMESTAMP WHERE"
                        + " id IN (:ids)",
                    Map.of("ids", rows.stream().map(row -> number(row, "id")).toList()));
        } catch (org.springframework.data.redis.RedisConnectionFailureException failure) {
            log.error("消息提醒暂未完成，将于下轮重试，数据库收件记录已保留", failure);
        }
    }

    /** 调度开关供隔离测试关闭定时器，不替换实际业务逻辑。 */
    @org.springframework.stereotype.Component
    @ConditionalOnProperty(
            name = "sail.message.delivery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @RequiredArgsConstructor
    public static class Scheduler {
        private final MessageDeliveryService delivery;

        /** 定时触发持久化任务，调度结束后才开始下一轮。 */
        @Scheduled(fixedDelayString = "${sail.message.delivery-delay:1500}")
        public void run() {
            delivery.deliverDue();
        }
    }
}
