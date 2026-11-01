package sail.system.service;

import cn.hutool.core.util.StrUtil;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import sail.common.core.BusinessException;

import java.util.Arrays;

/** 后台支持的可生效配置，声明输入类型与校验边界，不接受任意配置键。 */
@Getter
@RequiredArgsConstructor
public enum SystemSetting {
    WEBSITE_TITLE("website.title", "站点名称", "general", "text", "浏览器标题和侧栏品牌名称", "Sail Admin"),
    WEBSITE_LOGO(
            "website.logo",
            "站点标志",
            "general",
            "image",
            "上传图片用于侧栏标志和浏览器标签图标，支持 PNG、JPEG、GIF、WebP、BMP，最大 2 MiB",
            ""),
    MAX_ATTEMPTS(
            "login.max_attempts", "登录失败阈值", "security", "number", "连续失败达到此次数后锁定账号，1 至 60 次", "5"),
    LOCK_MINUTES(
            "login.lock_minutes", "账号锁定时长", "security", "number", "达到失败阈值后的锁定分钟数，1 至 60 分钟", "15"),
    MESSAGE_RECIPIENTS(
            "message.max_recipients",
            "单次最大发送人数",
            "message",
            "number",
            "单次发布接收人数上限，1 至 100000 人",
            "20000"),
    MESSAGE_SCHEDULE(
            "message.schedule_days", "最远定时天数", "message", "number", "定时发送最多可提前 1 至 365 天", "90"),
    MESSAGE_ATTACHMENTS(
            "message.max_attachments", "单条附件数量", "message", "number", "正文图片也计入附件，1 至 20 个", "10"),
    MESSAGE_RECALL(
            "message.recall_minutes",
            "允许撤回时长",
            "message",
            "number",
            "发布后允许撤回的分钟数，1 至 43200 分钟",
            "1440"),
    MESSAGE_REMINDER(
            "message.reminder_enabled",
            "顶部消息提醒",
            "message",
            "switch",
            "关闭后隐藏顶部消息铃铛，不影响个人收件箱与投递",
            "1");
    private final String key;
    private final String label;
    private final String group;
    private final String inputType;
    private final String help;
    private final String initialValue;

    /** 只查找显式支持的业务键，不允许调用方注入其他服务器参数。 */
    public static SystemSetting find(String key) {
        return Arrays.stream(values())
                .filter(item -> item.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new BusinessException("配置键不在受支持范围内"));
    }

    /** 按实际生效语义校验，不能把无法解析的值替换成其他配置。 */
    public void validate(String value) {
        if ("number".equals(inputType)
                && (value == null
                        || !value.matches("[0-9]{1,6}")
                        || Integer.parseInt(value) < getMin()
                        || Integer.parseInt(value) > getMax()))
            throw new BusinessException(label + "必须为 " + getMin() + " 至 " + getMax() + " 的整数");
        if ("switch".equals(inputType) && !java.util.Set.of("0", "1").contains(value))
            throw new BusinessException(label + "仅支持开启或关闭");
        if (this == WEBSITE_TITLE && (StrUtil.isBlank(value) || value.length() > 64))
            throw new BusinessException("站点名称必须为 1 至 64 个字符");
        if (this == WEBSITE_LOGO && value != null && value.length() > 2000)
            throw new BusinessException("站点标志地址不能超过 2000 个字符");
        if (this == WEBSITE_LOGO
                && StrUtil.isNotBlank(value)
                && !value.matches("^(https://[^\\s]+|/(?!/)[^\\s]*)$"))
            throw new BusinessException("站点标志仅支持站内路径或 HTTPS 图片地址");
    }

    /** 数字控件下界与后端校验共享同一声明。 */
    public int getMin() {
        return this == MESSAGE_REMINDER ? 0 : 1;
    }

    /** 不同业务字段使用各自实际含义的上界，不能沿用登录策略的六十。 */
    public int getMax() {
        return switch (this) {
            case MESSAGE_RECIPIENTS -> 100000;
            case MESSAGE_SCHEDULE -> 365;
            case MESSAGE_ATTACHMENTS -> 20;
            case MESSAGE_RECALL -> 43200;
            case MESSAGE_REMINDER -> 1;
            default -> 60;
        };
    }
}
