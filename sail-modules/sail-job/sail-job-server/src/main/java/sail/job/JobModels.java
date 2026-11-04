package sail.job;

import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import sail.common.core.ListQuery;

public final class JobModels {
    private JobModels() {}

    public record Input(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 64) String groupName,
            @NotBlank @Size(max = 64) String handler,
            @NotBlank @Size(max = 4000) String parameters,
            @NotBlank @Pattern(regexp = "CRON|INTERVAL") String scheduleType,
            @Size(max = 120) String cron,
            @Min(10) @Max(2592000) Integer intervalSeconds,
            @NotBlank @Size(max = 64) String zoneId,
            @NotBlank @Pattern(regexp = "SKIP|FIRE_ONCE") String misfirePolicy,
            @NotNull @Min(5) @Max(86400) Integer timeoutSeconds,
            @NotNull @Min(0) @Max(3) Integer maxRetries,
            @NotNull @Min(10) @Max(3600) Integer retryDelaySeconds,
            @NotNull @Min(1) @Max(365) Integer retentionDays,
            @NotNull @Min(0) Long version) {}

    public record Control(
            @NotNull @Min(1) Long version,
            @NotBlank @Pattern(regexp = "ENABLED|PAUSED|ARCHIVED") String status) {}

    public record Version(@NotNull @Min(1) Long version) {}

    public record Resolve(@NotBlank @Size(max = 500) String reason) {}

    @Getter
    @Setter
    public static class Query extends ListQuery {
        @Min(1)
        private Long jobId;

        @Size(max = 16)
        private String source;
    }

    public record Definition(
            long id,
            String name,
            String groupName,
            String handler,
            String parameters,
            String scheduleType,
            String cron,
            Integer intervalSeconds,
            String zoneId,
            String misfirePolicy,
            int timeoutSeconds,
            int maxRetries,
            int retryDelaySeconds,
            int retentionDays,
            String status,
            long version,
            String syncStatus,
            String syncError,
            Long activeRunId,
            Long nextFireAt,
            Long lastStartedAt,
            String lastStatus,
            long createdAt,
            long updatedAt) {}

    public record Execution(
            long id,
            long jobId,
            String jobName,
            long jobVersion,
            String handler,
            String parameters,
            String configSnapshot,
            String source,
            Long actorId,
            String actorName,
            String status,
            int attempt,
            Long parentRunId,
            long scheduledAt,
            Long startedAt,
            Long finishedAt,
            Long heartbeatAt,
            Long durationMs,
            String instanceId,
            String traceId,
            long succeeded,
            long failed,
            String summary,
            String resolution,
            long createdAt) {}
}
