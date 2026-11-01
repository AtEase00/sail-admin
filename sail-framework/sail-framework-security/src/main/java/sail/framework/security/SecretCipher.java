package sail.framework.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;

/** 使用环境主密钥对第三方密钥进行 AES-256-GCM 加密。 */
@Component
public class SecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${sail.security.master-key}") String encoded) {
        // 主密钥读取当前环境的独立配置，解码后必须满足 AES-256 长度。
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length != 32)
            throw new IllegalArgumentException("sail.security.master-key 必须为 Base64 编码的 32 字节密钥");
        key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        // 每次使用独立 nonce，并将认证标签与密文一起保存。
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("密钥加密失败", ex);
        }
    }

    public String decrypt(String encoded) {
        // 认证失败必须中止，不能用其他配置或空字符串替代秘密。
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length < 28) throw new IllegalArgumentException("密文格式错误");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("密钥解密失败", ex);
        }
    }
}
