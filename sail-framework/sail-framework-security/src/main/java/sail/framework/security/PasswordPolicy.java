package sail.framework.security;

import cn.hutool.core.util.StrUtil;

import sail.common.core.BusinessException;

import java.nio.charset.StandardCharsets;

/** BCrypt 口令长度策略，按 UTF-8 字节检查算法上限。 */
public final class PasswordPolicy {
    private PasswordPolicy() {
        /* 策略工具不实例化。 */
    }

    /** 初始化、新建、修改和重置密码共用校验，不额外要求字符种类。 */
    public static void validate(String password) {
        // 允许六位初始密码，仅保留非空、基本长度和 BCrypt 字节上限校验。
        if (StrUtil.isBlank(password)
                || StrUtil.length(password) < PasswordConstants.MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length
                        > PasswordConstants.MAX_PASSWORD_UTF8_BYTES)
            throw new BusinessException(
                    "密码至少 " + PasswordConstants.MIN_PASSWORD_LENGTH
                            + " 个字符且 UTF-8 编码不超过 "
                            + PasswordConstants.MAX_PASSWORD_UTF8_BYTES + " 字节");
    }
}
