package sail.job;

import org.quartz.*;

import sail.common.core.BusinessException;

import java.time.*;
import java.util.*;

final class JobSchedules {
    static final String GROUP = "sail-managed";

    private JobSchedules() {}

    static JobKey key(long id) {
        return new JobKey("job-" + id, GROUP);
    }

    static TriggerKey trigger(long id) {
        return new TriggerKey("schedule-" + id, GROUP);
    }

    static List<Long> preview(JobModels.Input input) {
        try {
            ZoneId zone = ZoneId.of(input.zoneId());
            long now = System.currentTimeMillis();
            List<Long> dates = new ArrayList<>();
            if ("INTERVAL".equals(input.scheduleType())) {
                if (input.intervalSeconds() == null) throw new BusinessException("请设置执行间隔");
                for (int i = 1; i <= 5; i++) dates.add(now + i * input.intervalSeconds() * 1000L);
            } else {
                var cron = new CronExpression(input.cron() == null ? "" : input.cron());
                cron.setTimeZone(TimeZone.getTimeZone(zone));
                Date next = new Date(now);
                for (int i = 0; i < 5; i++) {
                    Date previous = next;
                    next = cron.getNextValidTimeAfter(previous);
                    if (next == null) break;
                    if (i > 0 && next.getTime() - previous.getTime() < 10000)
                        throw new BusinessException("通用任务的执行间隔不能小于 10 秒");
                    dates.add(next.getTime());
                }
                if (dates.isEmpty()) throw new BusinessException("Cron 在未来没有可执行时间");
            }
            return dates;
        } catch (java.text.ParseException | DateTimeException | IllegalArgumentException e) {
            throw new BusinessException("Cron 或时区格式无效，请使用 Quartz Cron 和有效 IANA 时区");
        }
    }

    static Trigger build(JobModels.Definition job) {
        var builder =
                TriggerBuilder.newTrigger()
                        .withIdentity(trigger(job.id()))
                        .forJob(key(job.id()))
                        .usingJobData("version", Long.toString(job.version()))
                        .usingJobData("source", "SCHEDULED");
        if ("CRON".equals(job.scheduleType())) {
            var schedule =
                    CronScheduleBuilder.cronSchedule(job.cron())
                            .inTimeZone(TimeZone.getTimeZone(ZoneId.of(job.zoneId())));
            schedule =
                    "SKIP".equals(job.misfirePolicy())
                            ? schedule.withMisfireHandlingInstructionDoNothing()
                            : schedule.withMisfireHandlingInstructionFireAndProceed();
            return builder.withSchedule(schedule).build();
        }
        var schedule =
                SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(job.intervalSeconds())
                        .repeatForever();
        schedule =
                "SKIP".equals(job.misfirePolicy())
                        ? schedule.withMisfireHandlingInstructionNextWithRemainingCount()
                        : schedule.withMisfireHandlingInstructionNowWithRemainingCount();
        return builder.startAt(new Date(System.currentTimeMillis() + job.intervalSeconds() * 1000L))
                .withSchedule(schedule)
                .build();
    }
}
