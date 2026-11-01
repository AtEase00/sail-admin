package sail.common.core;

/** 操作审计业务类型，与系统字典 sys_operation_type 的业务值保持一致。 */
public enum OperationType {
    AUTO,
    INSERT,
    UPDATE,
    DELETE,
    QUERY,
    GRANT,
    LOGIN,
    LOGOUT,
    UPLOAD,
    PASSWORD,
    FORCE_LOGOUT,
    OTHER
}
