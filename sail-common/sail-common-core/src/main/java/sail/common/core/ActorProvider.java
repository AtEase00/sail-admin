package sail.common.core;

/** 基础设施获取真实操作者的契约，定时任务没有登录用户时返回空。 */
public interface ActorProvider {
    /** 返回实际登录账号，无认证上下文时返回空，不虚构系统用户。 */
    Long currentUserId();
}
