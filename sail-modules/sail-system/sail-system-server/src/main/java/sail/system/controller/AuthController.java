package sail.system.controller;

import cn.dev33.satoken.stp.StpUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.system.dto.SystemRequests;
import sail.system.service.*;

import java.util.Map;

/** 登录、当前身份、权限与密码接口。 */
@RestController
@RequestMapping("/api/system/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final UserService users;
    private final RbacService rbac;

    @PostMapping("/login")
    @OperationLog(value = "登录", type = sail.common.core.OperationType.LOGIN)
    public ApiResult<?> login(@Valid @RequestBody SystemRequests.Login v) {
        /* 登录服务负责密码与锁定。 */
        return ApiResult.ok(auth.login(v));
    }

    @PostMapping("/logout")
    @OperationLog(value = "退出登录", type = sail.common.core.OperationType.LOGOUT)
    public ApiResult<?> logout() {
        /* 撤销当前令牌。 */
        StpUtil.logout();
        return ApiResult.ok(null);
    }

    @GetMapping("/me")
    public ApiResult<?> me() {
        /* 只返回当前用户与有效权限。 */
        long id = StpUtil.getLoginIdAsLong();
        return ApiResult.ok(
                Map.of(
                        "user",
                        users.me(),
                        "roles",
                        rbac.roles(id),
                        "permissions",
                        rbac.permissions(id)));
    }

    @GetMapping("/menus")
    public ApiResult<?> menus() {
        /* 菜单展示与后端授权使用同一有效权限。 */
        return ApiResult.ok(rbac.visibleMenus());
    }

    @PutMapping("/password")
    @OperationLog(value = "修改密码", type = sail.common.core.OperationType.PASSWORD)
    public ApiResult<?> password(@Valid @RequestBody SystemRequests.Password v) {
        /* 修改后全部会话失效。 */
        auth.changePassword(v);
        return ApiResult.ok(null);
    }
}
