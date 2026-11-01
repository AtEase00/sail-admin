package sail.framework.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import sail.common.core.ListQuery;

/** 目录列表共用编号和时间条件；业务列由服务端固定指定，不接收客户端 SQL 列名。 */
public final class DirectoryQueries {
    private DirectoryQueries() {}

    /** 将公共条件绑定到 SQL，目录仍返回完整匹配集供树和下拉选项使用。 */
    public static <T> QueryWrapper<T> from(ListQuery query) {
        var range = query.createdRange();
        // MyBatis 使用绑定参数，所有列名均为工程内定义的固定列。
        return new QueryWrapper<T>()
                .eq(query.getId() != null, "id", query.getId())
                .ge(range.from() != null, "created_at", range.from())
                .le(range.to() != null, "created_at", range.to());
    }
}
