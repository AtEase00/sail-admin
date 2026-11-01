package sail.pay.dto;

import jakarta.validation.constraints.*;

import sail.pay.enums.ChannelType;

/** 支付请求，金额统一为人民币分。 */
public final class PayRequests {
    private PayRequests() {
        /* 请求模型命名空间。 */
    }

    /** 支付业务应用。 */
    public record App(
            @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_-]{1,63}") String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Boolean enabled) {}

    /** 密钥使用 PEM 字符串；更新传空时保留原密钥。 */
    public record Channel(
            @NotNull @Positive Long applicationId,
            @NotNull ChannelType type,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 64) String appId,
            @Size(max = 64) String merchantId,
            @Size(max = 100) String merchantSerial,
            @Size(max = 100) String publicKeyId,
            @Size(max = 10000) String privateKey,
            @Size(max = 10000) String publicKey,
            @Size(max = 64) String apiV3Key,
            @NotBlank @Size(max = 200) String notifyBaseUrl,
            @NotNull Boolean enabled) {}

    /** 相同应用的 businessNo 唯一，业务端决定实际应付金额。 */
    public record Order(
            @NotNull @Positive Long applicationId,
            @NotNull @Positive Long channelId,
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String businessNo,
            @NotBlank @Size(max = 120) String subject,
            @NotNull @Min(1) @Max(100000000) Long amount,
            @NotNull @Min(1) @Max(120) Integer expiresMinutes) {}

    /** 首期提供整单退款，避免隐含的余额与部分退款业务。 */
    public record Refund(@NotBlank @Size(max = 100) String reason) {}
}
