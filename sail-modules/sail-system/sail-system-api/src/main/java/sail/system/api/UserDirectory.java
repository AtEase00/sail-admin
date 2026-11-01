package sail.system.api;

/** 跨模块用户查询契约，不暴露数据库实体和登录凭据。 */
public interface UserDirectory {
    /** 查询指定用户是否存在且启用，不返回敏感账号资料。 */
    boolean isActive(long userId);

    /** 批量核对全部接收人，任何一个不存在或停用均返回否。 */
    boolean areActive(java.util.Collection<Long> userIds);
}
