package sail.framework.security;

import java.util.List;

/** 系统模块提供角色与权限，安全模块不依赖系统实现。 */
public interface PermissionProvider {
    /** 读取有效角色赋予用户的当前功能权限。 */
    List<String> permissions(long userId);

    /** 读取用户当前启用的角色编码。 */
    List<String> roles(long userId);
}
