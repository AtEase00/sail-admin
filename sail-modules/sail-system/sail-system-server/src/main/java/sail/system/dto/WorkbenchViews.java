package sail.system.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 工作台只读视图，不对外暴露令牌、凭据或会话键。 */
public final class WorkbenchViews {
    private WorkbenchViews() {
        // 统一管理工作台响应模型，不实例化命名空间。
    }

    /** 每天的审计成功、失败和登录失败次数。 */
    public record AuditDay(LocalDate date, long success, long failed, long loginFailures) {}

    /** 操作类型的真实发生次数。 */
    public record ActionCount(String action, long count) {}

    /** 最近操作关联可用账号资料，已删除账号保留原编号。 */
    public record RecentAudit(
            long id,
            Long userId,
            String username,
            String nickname,
            String action,
            boolean success,
            LocalDateTime createdAt) {}

    /** 最近七个自然日的操作统计，截止时间采用服务端业务时间。 */
    public record AuditOverview(
            LocalDateTime from,
            LocalDateTime to,
            List<AuditDay> days,
            List<ActionCount> actions,
            List<RecentAudit> recent) {}

    /** 一个账号的有效会话摘要，仅返回展示所需资料。 */
    public record OnlineUser(
            long id,
            String username,
            String nickname,
            long sessions,
            LocalDateTime latestLoginAt) {}

    /** 在线账号总数和会话总数，列表按最近登录排序，最多展示八个账号。 */
    public record OnlineOverview(long total, long sessionTotal, List<OnlineUser> records) {}
}
