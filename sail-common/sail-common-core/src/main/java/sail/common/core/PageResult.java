package sail.common.core;

import java.util.List;

/** 与数据库组件解耦的分页响应。 */
public record PageResult<T>(long total, long page, long size, List<T> records) {}
