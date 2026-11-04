package sail.job;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import sail.common.core.*;

import tools.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class JobStore {
    private final JdbcTemplate db;

    public JdbcTemplate db() {
        return db;
    }

    private final ObjectMapper json;

    static Long nullable(ResultSet r, String field) throws SQLException {
        long value = r.getLong(field);
        return r.wasNull() ? null : value;
    }

    static final RowMapper<JobModels.Definition> JOB =
            (r, n) ->
                    new JobModels.Definition(
                            r.getLong("id"),
                            r.getString("name"),
                            r.getString("group_name"),
                            r.getString("handler"),
                            r.getString("parameters"),
                            r.getString("schedule_type"),
                            r.getString("cron"),
                            (Integer) r.getObject("interval_seconds"),
                            r.getString("zone_id"),
                            r.getString("misfire_policy"),
                            r.getInt("timeout_seconds"),
                            r.getInt("max_retries"),
                            r.getInt("retry_delay_seconds"),
                            r.getInt("retention_days"),
                            r.getString("status"),
                            r.getLong("version"),
                            r.getString("sync_status"),
                            r.getString("sync_error"),
                            nullable(r, "active_run_id"),
                            nullable(r, "next_fire_at"),
                            nullable(r, "last_started_at"),
                            r.getString("last_status"),
                            r.getLong("created_at"),
                            r.getLong("updated_at"));
    static final RowMapper<JobModels.Execution> RUN =
            (r, n) ->
                    new JobModels.Execution(
                            r.getLong("id"),
                            r.getLong("job_id"),
                            r.getString("job_name"),
                            r.getLong("job_version"),
                            r.getString("handler"),
                            r.getString("parameters"),
                            r.getString("config_snapshot"),
                            r.getString("source"),
                            nullable(r, "actor_id"),
                            r.getString("actor_name"),
                            r.getString("status"),
                            r.getInt("attempt"),
                            nullable(r, "parent_run_id"),
                            r.getLong("scheduled_at"),
                            nullable(r, "started_at"),
                            nullable(r, "finished_at"),
                            nullable(r, "heartbeat_at"),
                            nullable(r, "duration_ms"),
                            r.getString("instance_id"),
                            r.getString("trace_id"),
                            r.getLong("succeeded"),
                            r.getLong("failed"),
                            r.getString("summary"),
                            r.getString("resolution"),
                            r.getLong("created_at"));

    JobModels.Definition job(long id, boolean lock) {
        var rows =
                db.query("SELECT * FROM sys_job WHERE id=?" + (lock ? " FOR UPDATE" : ""), JOB, id);
        if (rows.isEmpty()) throw new BusinessException(404, "任务不存在");
        return rows.getFirst();
    }

    JobModels.Execution run(long id) {
        var rows = db.query("SELECT * FROM sys_job_execution WHERE id=?", RUN, id);
        if (rows.isEmpty()) throw new BusinessException(404, "执行记录不存在或已按保留策略清理");
        return rows.getFirst();
    }

    long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        db.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                    return statement;
                },
                keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    long newRun(
            JobModels.Definition job,
            String source,
            Long actor,
            String actorName,
            int attempt,
            Long parent,
            long scheduled,
            String status) {
        return insert(
                "INSERT INTO"
                    + " sys_job_execution(job_id,job_name,job_version,handler,parameters,config_snapshot,source,actor_id,actor_name,status,attempt,parent_run_id,scheduled_at,trace_id,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                job.id(),
                job.name(),
                job.version(),
                job.handler(),
                job.parameters(),
                json.writeValueAsString(job),
                source,
                actor,
                actorName,
                status,
                attempt,
                parent,
                scheduled,
                UUID.randomUUID().toString().replace("-", ""),
                System.currentTimeMillis());
    }

    PageResult<JobModels.Definition> jobs(JobModels.Query q) {
        var args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            where += " AND (name LIKE ? OR group_name LIKE ? OR handler LIKE ?)";
            for (int i = 0; i < 3; i++) args.add("%" + q.getKeyword() + "%");
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            where += " AND status=?";
            args.add(q.getStatus());
        }
        if (q.getId() != null) {
            where += " AND id=?";
            args.add(q.getId());
        }
        long total =
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_job" + where, Long.class, args.toArray());
        args.add(q.getSize());
        args.add((q.getPage() - 1) * q.getSize());
        return new PageResult<>(
                total,
                q.getPage(),
                q.getSize(),
                db.query(
                        "SELECT * FROM sys_job" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        JOB,
                        args.toArray()));
    }

    PageResult<JobModels.Execution> runs(JobModels.Query q) {
        var args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (q.getJobId() != null) {
            where += " AND job_id=?";
            args.add(q.getJobId());
        }
        if (q.getKeyword() != null && !q.getKeyword().isBlank()) {
            where += " AND (job_name LIKE ? OR trace_id LIKE ?)";
            args.add("%" + q.getKeyword() + "%");
            args.add("%" + q.getKeyword() + "%");
        }
        if (q.getStatus() != null && !q.getStatus().isBlank()) {
            where += " AND status=?";
            args.add(q.getStatus());
        }
        if (q.getSource() != null && !q.getSource().isBlank()) {
            where += " AND source=?";
            args.add(q.getSource());
        }
        var range = q.createdRange();
        if (range.from() != null) {
            where += " AND created_at>=?";
            args.add(
                    range.from()
                            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                            .toInstant()
                            .toEpochMilli());
        }
        if (range.to() != null) {
            where += " AND created_at<=?";
            args.add(
                    range.to()
                            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                            .toInstant()
                            .toEpochMilli());
        }
        long total =
                db.queryForObject(
                        "SELECT COUNT(*) FROM sys_job_execution" + where,
                        Long.class,
                        args.toArray());
        args.add(q.getSize());
        args.add((q.getPage() - 1) * q.getSize());
        return new PageResult<>(
                total,
                q.getPage(),
                q.getSize(),
                db.query(
                        "SELECT * FROM sys_job_execution"
                                + where
                                + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        RUN,
                        args.toArray()));
    }

    static String truncate(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
}
