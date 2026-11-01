package sail.message.service;

import java.util.Set;

/** 消息发布、批量投递及内容白名单的共享约束。 */
public final class MessageConstants {
    private MessageConstants() {
        /* 常量类禁止实例化。 */
    }

    public static final int BATCH_SIZE = 500;
    public static final int MAX_ATTEMPTS = 3;
    public static final Set<String> CATEGORIES = Set.of("NOTICE", "BUSINESS", "SYSTEM");
    public static final Set<String> PRIORITIES = Set.of("NORMAL", "IMPORTANT", "URGENT");
    public static final Set<String> EDITABLE = Set.of("DRAFT", "CANCELLED");
    public static final Set<String> RECALLABLE =
            Set.of("SCHEDULED", "QUEUED", "SENDING", "SENT", "PARTIAL_FAILED", "FAILED");
}
