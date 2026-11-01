package sail.system.service;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.framework.security.PermissionProvider;
import sail.system.entity.*;
import sail.system.mapper.*;

import java.util.*;

/** 角色授权与数据范围，所有角色关系变更均在事务内批量完成。 */
@Service
@RequiredArgsConstructor
public class RbacService implements PermissionProvider {
    private final PermissionMapper relations;
    private final SysRoleMapper roles;
    private final SysMenuMapper menus;
    private final SysDeptMapper depts;
    private final SysUserMapper users;

    @Override
    public List<String> roles(long id) {
        /* 查询有效角色。 */
        return relations.roles(id);
    }

    @Override
    public List<String> permissions(long id) {
        // 超级管理员以数据库中的内置角色为准。
        return roles(id).contains(sail.framework.security.SecurityConstants.SUPER_ADMIN)
                ? List.of("*")
                : relations.permissions(id);
    }

    public List<Long> roleIds(long userId) {
        // 角色关系详情执行与用户详情相同的数据范围校验。
        checkUserAccess(userId);
        return relations.roleIds(userId);
    }

    public List<Long> postIds(long userId) {
        // 岗位关系只允许在当前操作者可见的用户范围内读取。
        checkUserAccess(userId);
        return relations.postIds(userId);
    }

    public List<Long> menuIds(long roleId) {
        // 仅返回授权关系编号，目录信息由目录服务提供。
        return relations.menuIds(roleId);
    }

    public List<Long> deptIds(long roleId) {
        // 指定部门范围与角色定义分别读取，供管理端回显。
        return relations.deptIds(roleId);
    }

    public List<SysMenu> visibleMenus() {
        long id = StpUtil.getLoginIdAsLong();
        List<SysMenu> enabled =
                menus.selectList(
                        Wrappers.<SysMenu>lambdaQuery()
                                .eq(SysMenu::getStatus, 1)
                                .orderByAsc(SysMenu::getSortOrder));
        if (roles(id).contains(sail.framework.security.SecurityConstants.SUPER_ADMIN))
            return enabled;
        // 已授权菜单补齐启用的祖先节点，目录本身无需虚设按钮权限。
        Map<Long, SysMenu> byId = new HashMap<>();
        enabled.forEach(m -> byId.put(m.getId(), m));
        Set<Long> visible = new HashSet<>();
        for (Long menuId : relations.visibleMenuIds(id)) {
            List<Long> branch = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            Long cursor = menuId;
            while (cursor != null && byId.containsKey(cursor) && visited.add(cursor)) {
                branch.add(cursor);
                cursor = byId.get(cursor).getParentId();
            }
            if (cursor == null) visible.addAll(branch);
        }
        return enabled.stream().filter(m -> visible.contains(m.getId())).toList();
    }

    public void restrictUsers(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser> query) {
        long id = StpUtil.getLoginIdAsLong();
        if (roles(id).contains(sail.framework.security.SecurityConstants.SUPER_ADMIN)) return;
        List<Long> ids = relations.roleIds(id);
        Set<Long> allowed = new HashSet<>();
        boolean self = false;
        SysUser current = users.selectById(id);
        // 各有效角色的数据范围取并集，不能从请求中传入任意部门冒充授权。
        for (SysRole role : ids.isEmpty() ? List.<SysRole>of() : roles.selectByIds(ids)) {
            if (role.getStatus() != 1) continue;
            switch (role.getDataScope()) {
                case 1:
                    return;
                case 2:
                    allowed.addAll(relations.deptIds(role.getId()));
                    break;
                case 3:
                    if (current.getDeptId() != null) allowed.add(current.getDeptId());
                    break;
                case 4:
                    if (current.getDeptId() != null) {
                        // 本部门子树单独计算，不能给其他角色的自定义部门额外扩展下级。
                        Set<Long> descendants = new HashSet<>();
                        descendants.add(current.getDeptId());
                        List<SysDept> all = depts.selectList(null);
                        boolean changed;
                        do {
                            changed = false;
                            for (SysDept dept : all)
                                if (descendants.contains(dept.getParentId()))
                                    changed |= descendants.add(dept.getId());
                        } while (changed);
                        allowed.addAll(descendants);
                    }
                    break;
                case 5:
                    self = true;
                    break;
                default:
                    throw new IllegalStateException("未知的数据权限范围");
            }
        }
        if (!self && allowed.isEmpty()) {
            query.apply("1=0");
            return;
        }
        boolean own = self;
        query.and(
                q -> {
                    if (!allowed.isEmpty()) q.in(SysUser::getDeptId, allowed);
                    if (own) {
                        if (!allowed.isEmpty()) q.or();
                        q.eq(SysUser::getId, id);
                    }
                });
    }

    public void checkUserAccess(long id) {
        // 查询单个用户同样执行数据范围校验，避免绕过分页接口越权。
        var q = Wrappers.<SysUser>lambdaQuery().eq(SysUser::getId, id);
        restrictUsers(q);
        if (users.selectCount(q) == 0) throw new BusinessException(403, "用户不存在或无权访问");
    }

    @Transactional
    public void assignUserRoles(long userId, List<Long> ids) {
        checkUserAccess(userId);
        ids = ids.stream().distinct().toList();
        if (!roles(StpUtil.getLoginIdAsLong())
                .contains(sail.framework.security.SecurityConstants.SUPER_ADMIN))
            throw new BusinessException(403, "只有超级管理员可以分配角色");
        // 防止自身失去最后的管理入口，内置账号角色不可移除。
        if (userId == 1 && !ids.contains(1L)) throw new BusinessException("内置管理员必须保留超级管理员角色");
        if (!ids.isEmpty() && roles.selectByIds(ids).size() != ids.size())
            throw new BusinessException("存在无效角色");
        relations.clearUserRoles(userId);
        if (!ids.isEmpty()) relations.userRoles(userId, ids);
        StpUtil.kickout(userId);
    }

    @Transactional
    public void assignRoleMenus(long roleId, List<Long> ids) {
        SysRole role = roles.selectById(roleId);
        if (role == null || Boolean.TRUE.equals(role.getBuiltIn()))
            throw new BusinessException("角色不存在或内置角色不可修改");
        ids = ids.stream().distinct().toList();
        if (!ids.isEmpty() && menus.selectByIds(ids).size() != ids.size())
            throw new BusinessException("存在无效菜单");
        // 先校验再替换，避免半成功授权。
        relations.clearRoleMenus(roleId);
        if (!ids.isEmpty()) relations.roleMenus(roleId, ids);
    }

    @Transactional
    public void assignRoleDepts(long roleId, List<Long> ids) {
        SysRole role = roles.selectById(roleId);
        if (role == null || Boolean.TRUE.equals(role.getBuiltIn()))
            throw new BusinessException("角色不存在或内置角色不可修改");
        ids = ids.stream().distinct().toList();
        if (!ids.isEmpty() && depts.selectByIds(ids).size() != ids.size())
            throw new BusinessException("存在无效部门");
        // 指定部门数据范围只存真实部门编号。
        relations.clearRoleDepts(roleId);
        if (!ids.isEmpty()) relations.roleDepts(roleId, ids);
    }
}
