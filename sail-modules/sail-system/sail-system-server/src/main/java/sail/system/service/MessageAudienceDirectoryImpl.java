package sail.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import sail.common.core.*;
import sail.system.api.MessageAudienceDirectory;
import sail.system.entity.*;
import sail.system.mapper.*;

import java.util.*;
import java.util.stream.Collectors;

/** 使用现有 RBAC 数据范围解析消息接收人，禁止越权枚举账号。 */
@Service
@RequiredArgsConstructor
public class MessageAudienceDirectoryImpl implements MessageAudienceDirectory {
    private final SysUserMapper users;
    private final SysDeptMapper depts;
    private final SysRoleMapper roles;
    private final SysPostMapper posts;
    private final RbacService rbac;
    private final NamedParameterJdbcTemplate jdbc;

    /** 候选始终以有效账号和当前数据权限为基础。 */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser> visible() {
        var query = Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, 1);
        rbac.restrictUsers(query);
        return query;
    }

    @Override
    public PageResult<Person> candidates(ListQuery query, Long deptId) {
        // 所有关键词与部门条件在数据库内过滤，分页不加载全体人员。
        var filter =
                visible()
                        .eq(deptId != null, SysUser::getDeptId, deptId)
                        .and(
                                StrUtil.isNotBlank(query.getKeyword()),
                                q ->
                                        q.like(SysUser::getUsername, query.getKeyword())
                                                .or()
                                                .like(SysUser::getNickname, query.getKeyword()))
                        .orderByAsc(SysUser::getId);
        var page = users.selectPage(new Page<>(query.getPage(), query.getSize()), filter);
        return new PageResult<>(
                page.getTotal(), query.getPage(), query.getSize(), people(page.getRecords()));
    }

    @Override
    public Map<String, List<Option>> options() {
        // 非全局人员只显示其可接触账号所属的组织、角色和岗位。
        boolean admin = StpUtil.hasRole(sail.framework.security.SecurityConstants.SUPER_ADMIN);
        var scoped =
                admin
                        ? List.<SysUser>of()
                        : users.selectList(visible().select(SysUser::getId, SysUser::getDeptId));
        var ids = scoped.stream().map(SysUser::getId).toList();
        var deptIds =
                scoped.stream()
                        .map(SysUser::getDeptId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Set<Long> roleIds = relationIds("sys_user_role", "role_id", ids);
        Set<Long> postIds = relationIds("sys_user_post", "post_id", ids);
        return Map.of(
                "departments",
                depts
                        .selectList(
                                Wrappers.<SysDept>lambdaQuery()
                                        .eq(SysDept::getStatus, 1)
                                        .orderByAsc(SysDept::getSortOrder))
                        .stream()
                        .filter(d -> admin || deptIds.contains(d.getId()))
                        .map(d -> new Option(d.getId(), d.getName(), d.getParentId()))
                        .toList(),
                "roles",
                roles.selectList(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getStatus, 1)).stream()
                        .filter(r -> admin || roleIds.contains(r.getId()))
                        .map(r -> new Option(r.getId(), r.getName(), null))
                        .toList(),
                "posts",
                posts.selectList(Wrappers.<SysPost>lambdaQuery().eq(SysPost::getStatus, 1)).stream()
                        .filter(p -> admin || postIds.contains(p.getId()))
                        .map(p -> new Option(p.getId(), p.getName(), null))
                        .toList());
    }

    /** 表名和字段名只取本类固定值，用户编号均通过绑定变量参与关系查询。 */
    private Set<Long> relationIds(String table, String field, List<Long> ids) {
        if (CollUtil.isEmpty(ids)) return Set.of();
        Set<Long> result = new HashSet<>();
        for (int start = 0; start < ids.size(); start += 500)
            result.addAll(
                    jdbc.queryForList(
                            "SELECT DISTINCT "
                                    + field
                                    + " FROM "
                                    + table
                                    + " WHERE deleted=0 AND user_id IN (:ids)",
                            Map.of("ids", ids.subList(start, Math.min(start + 500, ids.size()))),
                            Long.class));
        return result;
    }

    @Override
    public List<Person> resolve(Scope scope, int limit) {
        var allowed = options();
        // 明确选择的无效组织不静默扩大或缩小为其他范围。
        validate(scope.deptIds(), allowed.get("departments"));
        validate(scope.roleIds(), allowed.get("roles"));
        validate(scope.postIds(), allowed.get("posts"));
        var explicit = new HashSet<>(scope.userIds());
        explicit.addAll(scope.excludedIds());
        if (!explicit.isEmpty()
                && users.selectCount(visible().in(SysUser::getId, explicit)) != explicit.size())
            throw new BusinessException(403, "所选人员已停用、不存在或不在可发送范围");
        if (scope.all()) {
            StpUtil.checkPermission("message:send:all");
            if (!users.selectCount(visible())
                    .equals(
                            users.selectCount(
                                    Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, 1))))
                throw new BusinessException(403, "当前数据范围不允许向全系统发送");
        }
        Set<Long> deptIds = new HashSet<>(scope.deptIds());
        if (scope.includeChildren() && !deptIds.isEmpty()) {
            var all = depts.selectList(Wrappers.<SysDept>lambdaQuery().eq(SysDept::getStatus, 1));
            boolean changed;
            do {
                changed = false;
                for (var dept : all)
                    if (deptIds.contains(dept.getParentId())) changed |= deptIds.add(dept.getId());
            } while (changed);
        }
        Set<Long> related = new HashSet<>(scope.userIds());
        if (!scope.roleIds().isEmpty())
            related.addAll(
                    jdbc.queryForList(
                            "SELECT user_id FROM sys_user_role WHERE deleted=0 AND role_id IN"
                                + " (:ids)",
                            Map.of("ids", scope.roleIds()),
                            Long.class));
        if (!scope.postIds().isEmpty())
            related.addAll(
                    jdbc.queryForList(
                            "SELECT user_id FROM sys_user_post WHERE deleted=0 AND post_id IN"
                                + " (:ids)",
                            Map.of("ids", scope.postIds()),
                            Long.class));
        var query = visible();
        if (!scope.all()) {
            if (related.isEmpty() && deptIds.isEmpty()) throw new BusinessException("请选择接收范围");
            query.and(
                    q -> {
                        if (!related.isEmpty()) q.in(SysUser::getId, related);
                        if (!deptIds.isEmpty()) {
                            if (!related.isEmpty()) q.or();
                            q.in(SysUser::getDeptId, deptIds);
                        }
                    });
        }
        query.notIn(!scope.excludedIds().isEmpty(), SysUser::getId, scope.excludedIds());
        long count = users.selectCount(query);
        if (count == 0) throw new BusinessException("所选范围没有有效接收人");
        if (count > limit) throw new BusinessException("接收人数超过当前配置上限：" + limit);
        return people(users.selectList(query.orderByAsc(SysUser::getId)));
    }

    /** 请求必须完全命中可选组织，不以其他参数代替无效编号。 */
    private void validate(List<Long> ids, List<Option> allowed) {
        if (!allowed.stream().map(Option::id).collect(Collectors.toSet()).containsAll(ids))
            throw new BusinessException(403, "所选组织不存在、已停用或不在可发送范围");
    }

    /** 部门名称一次批量关联，没有部门的账号保持空值。 */
    private List<Person> people(List<SysUser> list) {
        var ids =
                list.stream().map(SysUser::getDeptId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> names =
                ids.isEmpty()
                        ? Map.of()
                        : depts.selectByIds(ids).stream()
                                .collect(Collectors.toMap(SysDept::getId, SysDept::getName));
        return list.stream()
                .map(
                        u ->
                                new Person(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getNickname(),
                                        u.getDeptId(),
                                        u.getDeptId() == null ? null : names.get(u.getDeptId())))
                .toList();
    }

    @Override
    public Set<Long> activeIds(Collection<Long> ids) {
        // 后台任务没有会话，仅核对冻结快照中的账号当前是否仍有效。
        if (CollUtil.isEmpty(ids)) return Set.of();
        return users
                .selectList(
                        Wrappers.<SysUser>lambdaQuery()
                                .select(SysUser::getId)
                                .eq(SysUser::getStatus, 1)
                                .in(SysUser::getId, ids))
                .stream()
                .map(SysUser::getId)
                .collect(Collectors.toSet());
    }
}
