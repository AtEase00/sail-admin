package sail.framework.mybatis;

import org.apache.ibatis.logging.slf4j.Slf4jImpl;

/** SQL 控制台日志适配器，保留语句及影响行数，不输出绑定参数和结果行。 */
public final class SqlConsoleLog extends Slf4jImpl {
    /** MyBatis 按 Mapper 语句名称创建日志实例，复用现有 SLF4J 级别和追踪号。 */
    public SqlConsoleLog(String loggerName) {
        super(loggerName);
    }

    @Override
    public void debug(String message) {
        // SQL 中的问号原样保留，不将密码、凭据或存储密钥等绑定值写入日志。
        if (message.contains("Parameters:")) return;
        super.debug(message);
    }

    @Override
    public boolean isTraceEnabled() {
        // MyBatis 在 TRACE 级别输出查询结果行，此适配器始终禁止结果数据日志。
        return false;
    }

    @Override
    public void trace(String message) {
        // 即使外部开启 TRACE，也不输出包含密码哈希、令牌或业务数据的结果内容。
    }
}
