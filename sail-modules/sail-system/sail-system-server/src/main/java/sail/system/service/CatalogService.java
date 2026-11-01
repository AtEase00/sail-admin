package sail.system.service;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.common.core.ListQuery;
import sail.framework.mybatis.BaseEntity;
import sail.framework.mybatis.DirectoryQueries;
import sail.system.dto.SystemRequests;
import sail.system.entity.*;
import sail.system.mapper.*;

import java.util.*;
import java.util.function.Function;

/** 角色、菜单、组织和字典配置管理，公共处理限于相同的目录约束。 */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final SysRoleMapper roles;
    private final SysMenuMapper menus;
    private final SysDeptMapper depts;
    private final SysPostMapper posts;
    private final SysDictionaryMapper dictionaries;
    private final SysDictionaryTypeMapper dictionaryTypes;
    private final SysConfigMapper configs;
    private final SysUserMapper users;
    private final PermissionMapper relations;

    /** 目录内部调用使用完整集合，管理页面显式传入真实 SQL 筛选条件。 */
    public List<SysRole> roles() {
        return roles(new ListQuery());
    }

    /** 在数据库过滤业务字段，不在前端隐藏不匹配记录。 */
    public List<SysRole> roles(ListQuery query) {
        var wrapper = DirectoryQueries.<SysRole>from(query);
        wrapper.and(
                StrUtil.isNotBlank(query.getKeyword()),
                q ->
                        q.like("name", query.getKeyword())
                                .or()
                                .like("code", query.getKeyword())
                                .or()
                                .like("remark", query.getKeyword()));
        var status = query.enabledStatus();
        wrapper.eq(status != null, "status", status);
        wrapper.like(StrUtil.isNotBlank(query.getRemark()), "remark", query.getRemark());
        return roles.selectList(wrapper.orderByAsc("id"));
    }

    /** 校验角色唯一性与内置保护后保存基础资料、数据范围及备注。 */
    public long role(Long id, SystemRequests.Role v) {
        SysRole e = id == null ? new SysRole() : roles.selectById(id);
        if (e == null) throw new BusinessException("角色不存在");
        if (Boolean.TRUE.equals(e.getBuiltIn())
                || sail.framework.security.SecurityConstants.SUPER_ADMIN.equals(v.code()))
            throw new BusinessException("内置角色不可修改或仿造");
        // 唯一性由业务校验与数据库唯一索引同时保证。
        if (roles.selectCount(
                        Wrappers.<SysRole>lambdaQuery()
                                .eq(SysRole::getCode, v.code())
                                .ne(id != null, SysRole::getId, id))
                > 0) throw new BusinessException("角色编码已存在");
        e.setName(v.name());
        e.setCode(v.code());
        e.setStatus(v.status());
        e.setDataScope(v.dataScope());
        // 空备注使用空串保存，确保编辑清空时不会被非空更新策略忽略。
        e.setRemark(v.remark() == null ? "" : v.remark());
        e.setBuiltIn(false);
        if (id == null) roles.insert(e);
        else roles.updateById(e);
        return e.getId();
    }

    @Transactional
    public void deleteRole(long id) {
        SysRole e = roles.selectById(id);
        if (e == null || Boolean.TRUE.equals(e.getBuiltIn()))
            throw new BusinessException("角色不存在或不可删除");
        if (!relations.userIds(id).isEmpty()) throw new BusinessException("请先解除用户角色关联");
        // 清理无业务归属的关系后再删除目录。
        relations.clearRoleMenus(id);
        relations.clearRoleDepts(id);
        roles.deleteById(id);
    }

    /** 目录内部调用使用完整集合，管理页面显式传入真实 SQL 筛选条件。 */
    public List<SysMenu> menus() {
        return menus(new ListQuery());
    }

    /** 在数据库过滤业务字段，不在前端隐藏不匹配记录。 */
    public List<SysMenu> menus(ListQuery query) {
        var wrapper = DirectoryQueries.<SysMenu>from(query);
        wrapper.and(
                StrUtil.isNotBlank(query.getKeyword()),
                q ->
                        q.like("name", query.getKeyword())
                                .or()
                                .like("permission", query.getKeyword())
                                .or()
                                .like("path", query.getKeyword()));
        var status = query.enabledStatus();
        wrapper.eq(status != null, "status", status);
        wrapper.orderByAsc("sort_order", "id");
        return ancestors(menus.selectList(wrapper), menus, SysMenu::getParentId);
    }

    public long menu(Long id, SystemRequests.Menu v) {
        SysMenu e = id == null ? new SysMenu() : menus.selectById(id);
        if (e == null) throw new BusinessException("菜单不存在");
        // 沿父链检查环，禁止目录指向自身或下级。
        Long p = v.parentId();
        Set<Long> visited = new HashSet<>();
        if (id != null) visited.add(id);
        while (p != null) {
            if (!visited.add(p)) throw new BusinessException("菜单不能形成循环");
            SysMenu parent = menus.selectById(p);
            if (parent == null || "BUTTON".equals(parent.getType()))
                throw new BusinessException("父菜单无效");
            p = parent.getParentId();
        }
        if ("BUTTON".equals(v.type()) && cn.hutool.core.util.StrUtil.isBlank(v.permission()))
            throw new BusinessException("按钮必须配置权限标识");
        e.setParentId(v.parentId());
        e.setName(v.name());
        e.setIcon(v.icon());
        e.setPermission(v.permission());
        e.setPath(v.path());
        e.setType(v.type());
        e.setSortOrder(v.sortOrder());
        e.setStatus(v.status());
        if (id == null) menus.insert(e);
        else menus.updateById(e);
        return e.getId();
    }

    public void deleteMenu(long id) {
        // 被引用节点不允许删除，避免留下悬空的授权。
        if (menus.selectCount(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getParentId, id)) > 0)
            throw new BusinessException("请先删除子菜单");
        if (relations.menuReferences(id) > 0) throw new BusinessException("请先解除菜单授权");
        menus.deleteById(id);
    }

    /** 目录内部调用使用完整集合，管理页面显式传入真实 SQL 筛选条件。 */
    public List<SysDept> depts() {
        return depts(new ListQuery());
    }

    /** 在数据库过滤业务字段，不在前端隐藏不匹配记录。 */
    public List<SysDept> depts(ListQuery query) {
        var wrapper = DirectoryQueries.<SysDept>from(query);
        wrapper.and(
                StrUtil.isNotBlank(query.getKeyword()), q -> q.like("name", query.getKeyword()));
        var status = query.enabledStatus();
        wrapper.eq(status != null, "status", status);
        wrapper.orderByAsc("sort_order", "id");
        return ancestors(depts.selectList(wrapper), depts, SysDept::getParentId);
    }

    public long dept(Long id, SystemRequests.Dept v) {
        SysDept e = id == null ? new SysDept() : depts.selectById(id);
        if (e == null) throw new BusinessException("部门不存在");
        // 部门树必须无环，未知父部门不自动替换为根节点。
        Long p = v.parentId();
        Set<Long> visited = new HashSet<>();
        if (id != null) visited.add(id);
        while (p != null) {
            if (!visited.add(p)) throw new BusinessException("部门不能形成循环");
            SysDept parent = depts.selectById(p);
            if (parent == null) throw new BusinessException("父部门不存在");
            p = parent.getParentId();
        }
        e.setParentId(v.parentId());
        e.setName(v.name());
        e.setSortOrder(v.sortOrder());
        e.setStatus(v.status());
        if (id == null) depts.insert(e);
        else depts.updateById(e);
        return e.getId();
    }

    public void deleteDept(long id) {
        // 组织节点仍有关联时拒绝删除。
        if (depts.selectCount(Wrappers.<SysDept>lambdaQuery().eq(SysDept::getParentId, id)) > 0
                || users.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getDeptId, id))
                        > 0) throw new BusinessException("部门仍有下级或用户");
        if (relations.deptReferences(id) > 0) throw new BusinessException("部门仍被数据权限引用");
        depts.deleteById(id);
    }

    /** 目录内部调用使用完整集合，管理页面显式传入真实 SQL 筛选条件。 */
    public List<SysPost> posts() {
        return posts(new ListQuery());
    }

    /** 在数据库过滤业务字段，不在前端隐藏不匹配记录。 */
    public List<SysPost> posts(ListQuery query) {
        var wrapper = DirectoryQueries.<SysPost>from(query);
        wrapper.and(
                StrUtil.isNotBlank(query.getKeyword()),
                q -> q.like("name", query.getKeyword()).or().like("code", query.getKeyword()));
        var status = query.enabledStatus();
        wrapper.eq(status != null, "status", status);
        return posts.selectList(wrapper.orderByAsc("id"));
    }

    public long post(Long id, SystemRequests.Post v) {
        SysPost e = id == null ? new SysPost() : posts.selectById(id);
        if (e == null) throw new BusinessException("岗位不存在");
        // 采用稳定岗位编码供后续业务引用。
        if (posts.selectCount(
                        Wrappers.<SysPost>lambdaQuery()
                                .eq(SysPost::getCode, v.code())
                                .ne(id != null, SysPost::getId, id))
                > 0) throw new BusinessException("岗位编码已存在");
        e.setName(v.name());
        e.setCode(v.code());
        e.setStatus(v.status());
        if (id == null) posts.insert(e);
        else posts.updateById(e);
        return e.getId();
    }

    /** 目录内部调用使用完整集合，管理页面显式传入真实 SQL 筛选条件。 */
    public List<SysDictionary> dictionaries() {
        return dictionaries(new ListQuery());
    }

    /** 在数据库过滤业务字段，不在前端隐藏不匹配记录。 */
    public List<SysDictionary> dictionaries(ListQuery query) {
        var wrapper = DirectoryQueries.<SysDictionary>from(query);
        wrapper.and(
                StrUtil.isNotBlank(query.getKeyword()),
                q ->
                        q.like("type_code", query.getKeyword())
                                .or()
                                .like("label", query.getKeyword())
                                .or()
                                .like("value", query.getKeyword()));
        var status = query.enabledStatus();
        wrapper.eq(status != null, "status", status);
        wrapper.eq(StrUtil.isNotBlank(query.getTypeCode()), "type_code", query.getTypeCode());
        wrapper.orderByAsc("sort_order", "id");
        return dictionaries.selectList(wrapper);
    }

    @Transactional
    public long dictionary(Long id, SystemRequests.Dictionary v) {
        SysDictionary e = id == null ? new SysDictionary() : dictionaries.selectById(id);
        if (e == null) throw new BusinessException("字典不存在");
        if (dictionaryTypes.selectCount(
                        Wrappers.<SysDictionaryType>lambdaQuery()
                                .eq(SysDictionaryType::getCode, v.typeCode()))
                == 0) throw new BusinessException("请先创建字典类型");
        if (Boolean.TRUE.equals(e.getBuiltIn())
                && (!Objects.equals(e.getTypeCode(), v.typeCode())
                        || !Objects.equals(e.getValue(), v.value())))
            throw new BusinessException("系统字典业务值不可修改，可调整标签、排序和颜色");
        // 同一类型内 value 唯一，避免业务状态渲染歧义。
        if (dictionaries.selectCount(
                        Wrappers.<SysDictionary>lambdaQuery()
                                .eq(SysDictionary::getTypeCode, v.typeCode())
                                .eq(SysDictionary::getValue, v.value())
                                .ne(id != null, SysDictionary::getId, id))
                > 0) throw new BusinessException("字典值已存在");
        // 同一字典只保留一个默认项，清理与保存处于同一事务。
        if (Boolean.TRUE.equals(v.isDefault()))
            dictionaries.update(
                    null,
                    Wrappers.<SysDictionary>lambdaUpdate()
                            .eq(SysDictionary::getTypeCode, v.typeCode())
                            .set(SysDictionary::getIsDefault, false));
        e.setColor(v.color() == null ? "default" : v.color());
        e.setRemark(StrUtil.emptyIfNull(v.remark()));
        e.setIsDefault(Boolean.TRUE.equals(v.isDefault()));
        e.setTypeCode(v.typeCode());
        e.setLabel(v.label());
        e.setValue(v.value());
        e.setSortOrder(v.sortOrder());
        e.setStatus(v.status());
        if (id == null) dictionaries.insert(e);
        else dictionaries.updateById(e);
        return e.getId();
    }

    /** 目录内部调用使用完整集合，管理页面显式传入真实 SQL 筛选条件。 */
    public List<SysConfig> configs() {
        return configs(new ListQuery());
    }

    /** 在数据库过滤业务字段，不在前端隐藏不匹配记录。 */
    public List<SysConfig> configs(ListQuery query) {
        var wrapper = DirectoryQueries.<SysConfig>from(query);
        wrapper.and(
                StrUtil.isNotBlank(query.getKeyword()),
                q ->
                        q.like("config_key", query.getKeyword())
                                .or()
                                .like("config_value", query.getKeyword())
                                .or()
                                .like("description", query.getKeyword()));
        return configs.selectList(wrapper.orderByAsc("id"));
    }

    public long config(Long id, SystemRequests.Config v) {
        // 旧参数入口与分组配置入口使用同一组受控定义和校验规则。
        SystemSetting.find(v.configKey()).validate(v.configValue());
        SysConfig e = id == null ? new SysConfig() : configs.selectById(id);
        if (e == null) throw new BusinessException("配置不存在");
        if (configs.selectCount(
                        Wrappers.<SysConfig>lambdaQuery()
                                .eq(SysConfig::getConfigKey, v.configKey())
                                .ne(id != null, SysConfig::getId, id))
                > 0) throw new BusinessException("配置键已存在");
        e.setConfigKey(v.configKey());
        e.setConfigValue(v.configValue());
        e.setDescription(v.description());
        if (id == null) configs.insert(e);
        else configs.updateById(e);
        return e.getId();
    }

    /** 仅为匹配节点批量补齐父链，不加载无关分支，保证查询结果仍能按层级展示。 */
    private <T extends BaseEntity> List<T> ancestors(
            List<T> matches, BaseMapper<T> mapper, Function<T, Long> parentId) {
        Map<Long, T> result = new LinkedHashMap<>();
        List<T> level = matches;
        while (!level.isEmpty()) {
            level.forEach(item -> result.put(item.getId(), item));
            // 已访问编号不再查询，即使历史异常数据存在环也不会无限循环。
            Set<Long> parents = new HashSet<>();
            for (T item : level) {
                Long parent = parentId.apply(item);
                if (parent != null && !result.containsKey(parent)) parents.add(parent);
            }
            level = parents.isEmpty() ? List.of() : mapper.selectByIds(parents);
        }
        return new ArrayList<>(result.values());
    }

    public void deletePost(long id) {
        // 有岗位关联时先解除关系，防止删除仍使用的组织资料。
        if (relations.postReferences(id) > 0) throw new BusinessException("岗位仍有关联用户");
        posts.deleteById(id);
    }

    public void deleteDictionary(long id) {
        var item = dictionaries.selectById(id);
        if (item == null) throw new BusinessException("字典数据不存在");
        if (Boolean.TRUE.equals(item.getBuiltIn()))
            throw new BusinessException("系统字典业务值不可删除，可调整显示标签");
        // 删除自定义展示资料，不修改业务枚举契约。
        dictionaries.deleteById(id);
    }
}
