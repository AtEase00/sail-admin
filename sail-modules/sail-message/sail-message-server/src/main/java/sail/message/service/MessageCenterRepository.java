package sail.message.service;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import sail.common.core.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** 消息发布与收件快照 SQL，所有外部条件均绑定，批处理由事务服务统一控制。 */
@Repository
@RequiredArgsConstructor
public class MessageCenterRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /** JDBC 列名按接口约定转为驼峰，时间保持 LocalDateTime 参与全局格式化。 */
    private final RowMapper<Map<String, Object>> mapper =
            (rs, index) -> {
                Map<String, Object> result = new LinkedHashMap<>();
                var metadata = rs.getMetaData();
                for (int col = 1; col <= metadata.getColumnCount(); col++) {
                    String name =
                            cn.hutool.core.util.StrUtil.toCamelCase(
                                    metadata.getColumnLabel(col).toLowerCase(Locale.ROOT));
                    Object value = rs.getObject(col);
                    result.put(
                            name,
                            value instanceof Timestamp timestamp
                                    ? timestamp.toLocalDateTime()
                                    : value);
                }
                return result;
            };

    /** 执行绑定参数查询，返回稳定接口字段。 */
    public List<Map<String, Object>> list(String sql, Map<String, ?> args) {
        return jdbc.query(sql, args, mapper);
    }

    /** 读取唯一对象；找不到时由业务层决定明确的错误语义。 */
    public Map<String, Object> one(String sql, Map<String, ?> args) {
        var rows = list(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 行锁在当前事务内保护状态转换与分批投递，支持多实例。 */
    public Map<String, Object> task(long id, boolean lock) {
        return one(
                "SELECT * FROM message_task WHERE id=:id AND deleted=0"
                        + (lock ? " FOR UPDATE" : ""),
                Map.of("id", id));
    }

    /** 插入后读取数据库实际生成的主键。 */
    public long insert(String sql, Map<String, ?> args) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource(args), holder, new String[] {"id"});
        return Objects.requireNonNull(holder.getKey()).longValue();
    }

    /** 状态变更返回影响行数，调用方校验乐观版本或状态。 */
    public int update(String sql, Map<String, ?> args) {
        return jdbc.update(sql, args);
    }

    /** 明确批量写入，避免逐名用户重复往返数据库。 */
    public void batch(String sql, List<? extends Map<String, ?>> rows) {
        if (rows.isEmpty()) return;
        jdbc.batchUpdate(
                sql,
                rows.stream().map(MapSqlParameterSource::new).toArray(SqlParameterSource[]::new));
    }

    /** 同一条件用于总数和分页，保证筛选作用于数据库结果。 */
    public PageResult<Map<String, Object>> page(
            String select, String from, Map<String, Object> args, ListQuery query, String order) {
        long total =
                Objects.requireNonNull(
                        jdbc.queryForObject("SELECT COUNT(*) " + from, args, Long.class));
        args.put("limit", query.getSize());
        args.put("offset", (query.getPage() - 1) * query.getSize());
        return new PageResult<>(
                total,
                query.getPage(),
                query.getSize(),
                list(select + " " + from + " " + order + " LIMIT :limit OFFSET :offset", args));
    }

    /** 数值字段必须真实存在，不能拿其他编号代替缺失字段。 */
    public static long number(Map<String, Object> row, String field) {
        return ((Number) Objects.requireNonNull(row.get(field), field)).longValue();
    }

    /** 布尔字段兼容 JDBC 驱动的布尔和数值表示，空值不视作开启。 */
    public static boolean flag(Map<String, Object> row, String field) {
        Object value = row.get(field);
        return Boolean.TRUE.equals(value)
                || (value instanceof Number number && number.intValue() == 1);
    }

    /** 从已经映射的业务字段读取原时间，不进行隐式时区替换。 */
    public static LocalDateTime time(Map<String, Object> row, String field) {
        return (LocalDateTime) row.get(field);
    }
}
