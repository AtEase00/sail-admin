/** 新建用户、重置及修改密码共用的长度约定，与后端六位密码策略保持一致。 */
export const PASSWORD_MIN_LENGTH = 6

/** 表单字符数上限；服务端另按 UTF-8 字节检查 BCrypt 的 72 字节算法上限。 */
export const PASSWORD_MAX_LENGTH = 72
