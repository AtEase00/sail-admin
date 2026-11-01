package sail.file.vo;

import sail.file.entity.StorageConfig;

/** 存储列表和详情不返回任何明文或密文凭据。 */
public record StorageView(
        long id,
        String name,
        String type,
        String endpoint,
        String bucket,
        String region,
        boolean enabled,
        boolean selected,
        boolean credentialsConfigured) {
    public static StorageView from(StorageConfig c, long selected) {
        // 只反馈秘密是否配置，禁止将脱敏占位符作为更新凭据。
        return new StorageView(
                c.getId(),
                c.getName(),
                c.getType(),
                c.getEndpoint(),
                c.getBucket(),
                c.getRegion(),
                Boolean.TRUE.equals(c.getEnabled()),
                c.getId() == selected,
                c.getAccessKeyCipher() != null && c.getSecretKeyCipher() != null);
    }
}
