package sail.file.dto;

import jakarta.validation.constraints.*;

import sail.framework.oss.StorageType;

/** 存储实例写入参数，空秘密表示更新时保留原值。 */
public record StorageRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull StorageType type,
        @Size(max = 255) String endpoint,
        @Size(max = 100) String bucket,
        @Size(max = 256) String accessKey,
        @Size(max = 512) String secretKey,
        @Size(max = 80) String region,
        @NotNull Boolean enabled) {}
