package sail.system.service;

import cn.hutool.core.util.StrUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.framework.security.PasswordPolicy;
import sail.system.entity.SysUser;
import sail.system.mapper.PermissionMapper;
import sail.system.mapper.SysUserMapper;

import java.util.List;

/** 系统模块自身负责初始管理员落库，启动装配层不直接访问业务 Mapper。 */
@Service
@RequiredArgsConstructor
public class SystemBootstrapService {
    private final SysUserMapper users;
    private final PermissionMapper relations;
    private final UserCredentialService credentials;

    @Transactional
    public void initialize(String username, String password, String salt) {
        // 后续启动不覆盖已有内置账号与人工修改过的口令。
        if (users.selectById(1L) != null) return;
        if (StrUtil.isBlank(username) || username.length() > 64)
            throw new IllegalStateException("首次启动必须提供不超过 64 位的管理员账号");
        // 初始化、新建和改密共用长度策略，开发配置可直接使用 123456。
        PasswordPolicy.validate(password);
        // 账号和内置角色关系在同一个事务创建，初始密码读取环境配置。
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername(username);
        admin.setNickname("系统管理员");
        admin.setStatus(1);
        admin.setFailedAttempts(0);
        users.insert(admin);
        // 初始管理员也使用独立凭据表，不将密码材料写入 sys_user。
        credentials.createInitialAdministrator(admin.getId(), password, salt);
        relations.userRoles(1L, List.of(1L));
    }
}
