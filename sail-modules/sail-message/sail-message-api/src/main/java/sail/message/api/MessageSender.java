package sail.message.api;

import java.util.List;
import java.util.Map;

/** 模块间站内消息发送契约，业务模块提供明确接收人和变量。 */
public interface MessageSender {
    /** 使用已启用模板批量发送，返回实际持久化的接收人消息数量。 */
    int send(String templateCode, List<Long> recipientIds, Map<String, String> variables);
}
