package sail.message.enums;

/** 发布任务状态，个人已读、确认和归档状态独立保存。 */
public enum MessageState {
    DRAFT,
    SCHEDULED,
    QUEUED,
    SENDING,
    SENT,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED,
    WITHDRAWN
}
