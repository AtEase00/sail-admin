package sail.framework.security;

/** 密码长度及 BCrypt 编码约定，独立随机盐由算法的安全随机数生成器产生。 */
public final class PasswordConstants {
    /** 允许直接使用六位初始密码，后续由用户在后台修改。 */
    public static final int MIN_PASSWORD_LENGTH = 6;

    /** BCrypt 的输入上限按 UTF-8 字节计算，不能按字符截断密码。 */
    public static final int MAX_PASSWORD_UTF8_BYTES = 72;

    public static final int BCRYPT_STRENGTH = 12;

    /** 标准 BCrypt 编码前 29 位包含版本、成本参数和 128 位随机盐。 */
    public static final int BCRYPT_SALT_PREFIX_LENGTH = 29;

    /** 仅接受与当前编码成本一致的标准 BCrypt 盐，最后一位限定为规范 Base64 编码。 */
    public static final String BCRYPT_SALT_PATTERN = "\\$2[aby]\\$12\\$[./A-Za-z0-9]{21}[.Oeu]";

    private PasswordConstants() {
        // 常量工具类不允许实例化。
    }
}
