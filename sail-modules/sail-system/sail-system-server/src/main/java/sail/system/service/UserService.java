package sail.system.service;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.*;
import sail.system.dto.SystemRequests;
import sail.system.entity.*;
import sail.system.mapper.*;
import sail.system.vo.UserView;

/** 用户管理，统一执行数据权限与敏感字段隔离。 */
@Service
@RequiredArgsConstructor
public class UserService {
    private final SysUserMapper users;
    private final SysDeptMapper depts;
    private final SysPostMapper posts;
    private final PermissionMapper relations;
    private final UserCredentialService credentials;
    private final RbacService rbac;

    public PageResult<UserView> page(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 名称查询走参数绑定，分页上限在 Controller 与插件双重限制。
        var q =
                Wrappers.<SysUser>lambdaQuery()
                        .ge(range.from() != null, SysUser::getCreatedAt, range.from())
                        .le(range.to() != null, SysUser::getCreatedAt, range.to())
                        .and(cn.hutool.core.util.StrUtil.isNotBlank(keyword), condition -> condition
                                .like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword))
                        .eq(query.enabledStatus() != null, SysUser::getStatus, query.enabledStatus())
                        .orderByDesc(SysUser::getId);
        rbac.restrictUsers(q);
        var data = users.selectPage(new Page<>(page, size), q);
        return new PageResult<>(
                data.getTotal(),
                page,
                size,
                data.getRecords().stream().map(UserView::from).toList());
    }

    public UserView me() {
        /* 只读取当前认证账号资料。 */
        return UserView.from(users.selectById(StpUtil.getLoginIdAsLong()));
    }

    public sail.system.vo.ProfileView profile() {
        long id = StpUtil.getLoginIdAsLong();
        SysUser user = users.selectById(id);
        SysDept dept = user.getDeptId() == null ? null : depts.selectById(user.getDeptId());
        var postIds = relations.postIds(id);
        var names = postIds.isEmpty() ? java.util.List.<String>of()
                : posts.selectByIds(postIds).stream()
                        .filter(post -> post.getStatus() == 1).map(SysPost::getName).toList();
        return new sail.system.vo.ProfileView(UserView.from(user),
                dept == null ? null : dept.getName(), names, user.getCreatedAt());
    }

    public UserView updateProfile(SystemRequests.Profile input) {
        // 只更新当前会话的昵称，不能覆盖并发管理员修改的部门、状态或授权。
        long id = StpUtil.getLoginIdAsLong();
        int updated = users.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id).eq(SysUser::getStatus, 1)
                .set(SysUser::getNickname, input.nickname().strip())
                .set(SysUser::getUpdatedAt, java.time.LocalDateTime.now()));
        if (updated != 1) throw new BusinessException("账号不可用，请重新登录");
        return me();
    }

    public UserView get(long id) {
        /* 详情与列表采用相同数据范围。 */
        rbac.checkUserAccess(id);
        return UserView.from(users.selectById(id));
    }

    @Transactional
    public long create(SystemRequests.UserCreate input) {
        // 创建账号仅允许全局管理员，避免以部门权限创建越界账号。
        StpUtil.checkRole(sail.framework.security.SecurityConstants.SUPER_ADMIN);
        sail.framework.security.PasswordPolicy.validate(input.password());
        checkDept(input.deptId());
        if (users.selectCount(
                        Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, input.username()))
                > 0) throw new BusinessException("用户名已存在");
        SysUser u = new SysUser();
        u.setUsername(input.username());
        u.setNickname(input.nickname());
        u.setDeptId(input.deptId());
        u.setStatus(input.status());
        u.setFailedAttempts(0);
        users.insert(u);
        // 用户资料与独立凭据必须一并提交，原始口令只传给凭据服务。
        credentials.create(u.getId(), input.password());
        return u.getId();
    }

    public void update(long id, SystemRequests.UserUpdate input) {
        rbac.checkUserAccess(id);
        checkDept(input.deptId());
        SysUser u = users.selectById(id);
        if (id == 1 && input.status() != 1) throw new BusinessException("不能停用内置管理员");
        if (!rbac.roles(StpUtil.getLoginIdAsLong())
                        .contains(sail.framework.security.SecurityConstants.SUPER_ADMIN)
                && !java.util.Objects.equals(input.deptId(), u.getDeptId()))
            throw new BusinessException(403, "只有超级管理员可以调整部门");
        // 更新时不允许修改用户名、密码哈希或审计字段。
        u.setNickname(input.nickname());
        u.setDeptId(input.deptId());
        u.setStatus(input.status());
        users.updateById(u);
        if (input.status() == 0) StpUtil.kickout(id);
    }

    @Transactional
    public void reset(long id, String password) {
        rbac.checkUserAccess(id);
        StpUtil.checkRole(sail.framework.security.SecurityConstants.SUPER_ADMIN);
        sail.framework.security.PasswordPolicy.validate(password);
        // 密码修改后立即强制重新登录。
        SysUser u = users.selectById(id);
        credentials.reset(id, password);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.updateById(u);
        StpUtil.kickout(id);
    }

    @Transactional
    public void delete(long id) {
        StpUtil.checkRole(sail.framework.security.SecurityConstants.SUPER_ADMIN);
        rbac.checkUserAccess(id);
        if (id == 1 || id == StpUtil.getLoginIdAsLong())
            throw new BusinessException("不能删除内置管理员或当前账号");
        // 保留主键和历史供消息、文件及审计关联，逻辑删除写入 deleted=id。
        users.update(
                null,
                Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId, id).set(SysUser::getStatus, 0));
        users.deleteById(id);
        credentials.delete(id);
        relations.clearUserRoles(id);
        relations.clearUserPosts(id);
        StpUtil.kickout(id);
    }

    @Transactional
    public void assignPosts(long id, java.util.List<Long> ids) {
        rbac.checkUserAccess(id);
        ids = ids.stream().distinct().toList();
        if (!ids.isEmpty() && posts.selectByIds(ids).size() != ids.size())
            throw new BusinessException("存在无效岗位");
        // 岗位关系使用批量写入。
        relations.clearUserPosts(id);
        if (!ids.isEmpty()) relations.userPosts(id, ids);
    }

    private void checkDept(Long id) {
        // 未提供部门保持空；提供时必须存在且启用。
        if (id == null) return;
        SysDept dept = depts.selectById(id);
        if (dept == null || dept.getStatus() != 1) throw new BusinessException("部门不存在或已停用");
    }
}
