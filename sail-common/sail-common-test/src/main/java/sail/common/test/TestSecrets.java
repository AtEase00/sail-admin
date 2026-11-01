package sail.common.test;

import java.security.SecureRandom;
import java.util.Base64;

/** 隔离测试专用密钥生成工具，禁止作为生产默认配置。 */
public final class TestSecrets {
    private TestSecrets() {
        /* 工具类不实例化。 */
    }

    public static String aes256() {
        // 每次测试生成独立主密钥，不依赖开发机秘密。
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
