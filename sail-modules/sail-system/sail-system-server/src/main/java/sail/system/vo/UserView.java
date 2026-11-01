package sail.system.vo;

import sail.system.entity.SysUser;

/** 安全用户视图，不包含密码哈希和登录失败计数。 */
public record UserView(Long id, String username, String nickname, Long deptId, Integer status) {
    public static UserView from(SysUser u) {
        // 显式映射允许对外展示的字段。
        return new UserView(
                u.getId(), u.getUsername(), u.getNickname(), u.getDeptId(), u.getStatus());
    }
}
