package sail.system.api;

import sail.common.core.PageResult;

import java.util.*;

/** 消息选人与组织范围契约，仅返回发送范围内的必要展示资料。 */
public interface MessageAudienceDirectory {
    /** 接收规则取并集再排除用户，部门是否包含下级由调用者明确指定。 */
    record Scope(
            List<Long> userIds,
            List<Long> deptIds,
            List<Long> roleIds,
            List<Long> postIds,
            List<Long> excludedIds,
            boolean includeChildren,
            boolean all) {}

    /** 发布时保存的人员快照，未知部门保持为空。 */
    record Person(long id, String username, String nickname, Long deptId, String deptName) {}

    /** 可搜索的组织项，不携带权限或凭据。 */
    record Option(long id, String name, Long parentId) {}

    /** 当前操作者可见的组织目录。 */
    Map<String, List<Option>> options();

    /** 用户候选按后端数据范围分页，不依赖用户管理菜单权限。 */
    PageResult<Person> candidates(sail.common.core.ListQuery query, Long deptId);

    /** 在发送权限范围内校验并解析规则，超过上限拒绝发布。 */
    List<Person> resolve(Scope scope, int limit);

    /** 后台分批投递时批量核对实际启用账号。 */
    Set<Long> activeIds(Collection<Long> ids);
}
