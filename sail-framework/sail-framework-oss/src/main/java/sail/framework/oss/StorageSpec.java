package sail.framework.oss;

/** 单个存储配置的运行参数，秘密仅在适配器调用期间解密使用。 */
public record StorageSpec(
        long id,
        StorageType type,
        String endpoint,
        String bucket,
        String accessKey,
        String secretKey,
        String region) {}
