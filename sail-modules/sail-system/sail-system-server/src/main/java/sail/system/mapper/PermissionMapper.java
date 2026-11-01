package sail.system.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/** 角色权限及关系表的批量访问，SQL 参数均使用绑定变量。 */
@Mapper
public interface PermissionMapper {
    @Select(
            "SELECT DISTINCT m.permission FROM sys_menu m JOIN sys_role_menu rm ON m.id=rm.menu_id"
                + " JOIN sys_user_role ur ON ur.role_id=rm.role_id JOIN sys_role r ON"
                + " r.id=ur.role_id JOIN sys_user u ON u.id=ur.user_id WHERE ur.user_id=#{userId}"
                + " AND r.status=1 AND u.status=1 AND m.status=1 AND m.permission IS NOT NULL AND"
                + " ur.deleted=0 AND rm.deleted=0 AND r.deleted=0 AND u.deleted=0 AND m.deleted=0")
    List<String> permissions(long userId);

    @Select(
            "SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id JOIN sys_user u"
                + " ON u.id=ur.user_id WHERE ur.user_id=#{userId} AND r.status=1 AND u.status=1 AND"
                + " ur.deleted=0 AND r.deleted=0 AND u.deleted=0")
    List<String> roles(long userId);

    /** 读取有效角色直接分配的启用菜单，目录祖先由业务层补齐。 */
    @Select(
            "SELECT DISTINCT m.id FROM sys_menu m JOIN sys_role_menu rm ON m.id=rm.menu_id JOIN"
                + " sys_user_role ur ON ur.role_id=rm.role_id JOIN sys_role r ON r.id=ur.role_id"
                + " JOIN sys_user u ON u.id=ur.user_id WHERE ur.user_id=#{userId} AND r.status=1"
                + " AND u.status=1 AND m.status=1 AND ur.deleted=0 AND rm.deleted=0 AND r.deleted=0"
                + " AND u.deleted=0 AND m.deleted=0")
    List<Long> visibleMenuIds(long userId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id=#{userId} AND deleted=0")
    List<Long> roleIds(long userId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id=#{roleId} AND deleted=0")
    List<Long> menuIds(long roleId);

    @Select("SELECT dept_id FROM sys_role_dept WHERE role_id=#{roleId} AND deleted=0")
    List<Long> deptIds(long roleId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id=#{roleId} AND deleted=0")
    List<Long> userIds(long roleId);

    @Update("UPDATE sys_user_role SET deleted=id WHERE user_id=#{userId} AND deleted=0")
    int clearUserRoles(long userId);

    @Update("UPDATE sys_role_menu SET deleted=id WHERE role_id=#{roleId} AND deleted=0")
    int clearRoleMenus(long roleId);

    @Update("UPDATE sys_role_dept SET deleted=id WHERE role_id=#{roleId} AND deleted=0")
    int clearRoleDepts(long roleId);

    @Update("UPDATE sys_user_post SET deleted=id WHERE user_id=#{userId} AND deleted=0")
    int clearUserPosts(long userId);

    @Select("SELECT post_id FROM sys_user_post WHERE user_id=#{userId} AND deleted=0")
    List<Long> postIds(long userId);

    @Insert(
            "<script>INSERT INTO sys_user_role(user_id,role_id) VALUES <foreach collection='ids'"
                    + " item='id' separator=','>(#{owner},#{id})</foreach></script>")
    int userRoles(@Param("owner") long owner, @Param("ids") List<Long> ids);

    @Insert(
            "<script>INSERT INTO sys_role_menu(role_id,menu_id) VALUES <foreach collection='ids'"
                    + " item='id' separator=','>(#{owner},#{id})</foreach></script>")
    int roleMenus(@Param("owner") long owner, @Param("ids") List<Long> ids);

    @Insert(
            "<script>INSERT INTO sys_role_dept(role_id,dept_id) VALUES <foreach collection='ids'"
                    + " item='id' separator=','>(#{owner},#{id})</foreach></script>")
    int roleDepts(@Param("owner") long owner, @Param("ids") List<Long> ids);

    @Insert(
            "<script>INSERT INTO sys_user_post(user_id,post_id) VALUES <foreach collection='ids'"
                    + " item='id' separator=','>(#{owner},#{id})</foreach></script>")
    int userPosts(@Param("owner") long owner, @Param("ids") List<Long> ids);

    @Select("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id=#{id} AND deleted=0")
    long menuReferences(long id);

    @Select("SELECT COUNT(*) FROM sys_role_dept WHERE dept_id=#{id} AND deleted=0")
    long deptReferences(long id);

    @Select("SELECT COUNT(*) FROM sys_user_post WHERE post_id=#{id} AND deleted=0")
    long postReferences(long id);
}
