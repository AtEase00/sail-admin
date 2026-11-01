package sail.framework.web;

import lombok.RequiredArgsConstructor;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;

/** JSON 结构化脱敏后再截断，不把密码、令牌及私钥写入日志和审计库。 */
@RequiredArgsConstructor
public class LogSanitizer {
    private final ObjectMapper mapper;

    /** 仅序列化明确的 JSON 资料，不读取附件内容或任意对象的 toString。 */
    public String value(Object value) {
        var node = mapper.valueToTree(value);
        redact(node);
        return limit(mapper.writeValueAsString(node));
    }

    /** 正文无法完整解析时不回退打印原文，以免残缺 JSON 泄露秘密。 */
    public String json(String text) {
        if (text.isBlank()) return "";
        try {
            var node = mapper.readTree(text);
            // 无字段名的标量正文无法按字段规则脱敏，不输出原始文本。
            if (!node.isObject() && !node.isArray()) return "[正文不是结构化 JSON，未记录原文]";
            redact(node);
            return limit(mapper.writeValueAsString(node));
        } catch (JacksonException ex) {
            return "[正文非完整 JSON，未记录原文]";
        }
    }

    /** 逐层检查字段名，同时覆盖对象数组中的凭据。 */
    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            var names = new ArrayList<>(object.propertyNames());
            for (String name : names) {
                if (RequestLogConstants.SENSITIVE_FIELD
                        .matcher(name.replace("_", "").replace("-", ""))
                        .matches()) object.put(name, "[已脱敏]");
                else redact(object.get(name));
            }
        } else if (node.isArray()) node.forEach(this::redact);
    }

    /** 保存明确的截断标记，避免误以为正文完整。 */
    private String limit(String text) {
        return text.length() <= RequestLogConstants.MAX_BODY_LENGTH
                ? text
                : text.substring(0, RequestLogConstants.MAX_BODY_LENGTH) + "[已截断]";
    }
}
