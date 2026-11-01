package sail.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import sail.system.dto.WorkbenchViews;

import java.time.LocalDateTime;
import java.util.List;

/** 工作台按时间索引在数据库汇总审计，避免把全量日志传到前端。 */
@Mapper
public interface WorkbenchMapper {
    /** 按自然日统计，逻辑删除记录不参与聚合。 */
    @Select(
            """
            SELECT DATE(created_at) AS date,
                   SUM(CASE WHEN success = TRUE THEN 1 ELSE 0 END) AS success,
                   SUM(CASE WHEN success = FALSE THEN 1 ELSE 0 END) AS failed,
                   SUM(CASE WHEN success = FALSE AND action = '登录' THEN 1 ELSE 0 END) AS login_failures
              FROM sys_audit
             WHERE deleted = 0 AND created_at >= #{from} AND created_at < #{to}
             GROUP BY DATE(created_at) ORDER BY date
            """)
    List<WorkbenchViews.AuditDay> days(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 最近七日操作次数最多的六类操作。 */
    @Select(
            """
            SELECT action, COUNT(*) AS count FROM sys_audit
             WHERE deleted = 0 AND created_at >= #{from} AND created_at < #{to}
             GROUP BY action ORDER BY count DESC, action LIMIT 6
            """)
    List<WorkbenchViews.ActionCount> actions(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 单次关联操作者资料，避免逐条日志查询账号。 */
    @Select(
            """
            SELECT a.id, a.user_id, u.username, u.nickname, a.action, a.success, a.created_at
              FROM sys_audit a LEFT JOIN sys_user u ON u.id = a.user_id AND u.deleted = 0
             WHERE a.deleted = 0 AND a.created_at >= #{from} AND a.created_at < #{to}
             ORDER BY a.created_at DESC, a.id DESC LIMIT 8
            """)
    List<WorkbenchViews.RecentAudit> recent(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
