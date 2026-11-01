package sail.system.service;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.*;
import sail.system.dto.SystemRequests;
import sail.system.entity.SysUser;
import sail.system.mapper.SysUserMapper;

import java.time.*;
import java.util.Map;

/** 密码登录及账号锁定策略。 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final SysUserMapper users;
    private final sail.system.mapper.SysConfigMapper configs;
    private final UserCredentialService credentials;

    public Map<String, Object> login(SystemRequests.Login request) {
        // 账号不存在和密码错误使用相同响应，失败计数存数据库以支持多实例。
        SysUser user =
                users.selectOne(
                        Wrappers.<SysUser>lambdaQuery()
                                .eq(SysUser::getUsername, request.username()));
        if (user == null) throw new BusinessException(401, "账号或密码错误");
        // 登录尚未建立会话时，审计使用查到的真实账号编号。
        if (RequestAuditContext.current() != null)
            RequestAuditContext.current().setUserId(user.getId());
        if (user.getStatus() != 1
                || (user.getLockedUntil() != null
                        && user.getLockedUntil().isAfter(LocalDateTime.now())))
            throw new BusinessException(401, "账号不可用或暂时锁定");
        if (!credentials.matches(user.getId(), request.password())) {
            if (user.getLockedUntil() != null
                    && user.getLockedUntil().isBefore(LocalDateTime.now()))
                users.update(
                        null,
                        Wrappers.<SysUser>lambdaUpdate()
                                .eq(SysUser::getId, user.getId())
                                .set(SysUser::getFailedAttempts, 0)
                                .set(SysUser::getLockedUntil, null));
            users.update(
                    null,
                    Wrappers.<SysUser>lambdaUpdate()
                            .eq(SysUser::getId, user.getId())
                            .setSql("failed_attempts=failed_attempts+1")
                            .set(SysUser::getUpdatedAt, LocalDateTime.now()));
            // 达到阈值后锁定十五分钟，重新登录成功前保持失败计数。
            users.update(
                    null,
                    Wrappers.<SysUser>lambdaUpdate()
                            .eq(SysUser::getId, user.getId())
                            .ge(SysUser::getFailedAttempts, setting("login.max_attempts", 5))
                            .set(
                                    SysUser::getLockedUntil,
                                    LocalDateTime.now()
                                            .plusMinutes(setting("login.lock_minutes", 15))));
            throw new BusinessException(401, "账号或密码错误");
        }
        users.update(
                null,
                Wrappers.<SysUser>lambdaUpdate()
                        .eq(SysUser::getId, user.getId())
                        .set(SysUser::getFailedAttempts, 0)
                        .set(SysUser::getLockedUntil, null));
        // Redis 会话交由 Sa-Token 管理，返回真实登录令牌。
        StpUtil.login(user.getId());
        return Map.of(
                "tokenName",
                StpUtil.getTokenName(),
                "tokenValue",
                StpUtil.getTokenValue(),
                "timeout",
                StpUtil.getTokenTimeout());
    }

    @Transactional
    public void changePassword(SystemRequests.Password request) {
        // 先验证旧密码，再使该账号全部会话失效。
        SysUser user = users.selectById(StpUtil.getLoginIdAsLong());
        credentials.change(user.getId(), request.oldPassword(), request.newPassword());
        StpUtil.kickout(user.getId());
    }

    private int setting(String key, int initial) {
        // 初始化脚本保证配置存在，缺失时明确使用产品默认策略。
        var c =
                configs.selectOne(
                        Wrappers.<sail.system.entity.SysConfig>lambdaQuery()
                                .eq(sail.system.entity.SysConfig::getConfigKey, key));
        return c == null ? initial : Integer.parseInt(c.getConfigValue());
    }
}
