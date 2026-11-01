package sail.message.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.message.api.MessageSender;
import sail.message.dto.MessageRequests;

import java.util.*;

/** 站内消息契约本地适配，复用真实发送校验。 */
@Service
@RequiredArgsConstructor
public class MessageSenderImpl implements MessageSender {
    private final MessageService messages;

    @Override
    public int send(String code, List<Long> recipients, Map<String, String> variables) {
        // 内部调用同样限制批量大小，不提供无效值兜底。
        if (recipients == null
                || recipients.isEmpty()
                || recipients.size() > 500
                || variables == null) throw new sail.common.core.BusinessException("消息接收人或变量无效");
        return messages.send(new MessageRequests.Send(code, recipients, variables));
    }
}
