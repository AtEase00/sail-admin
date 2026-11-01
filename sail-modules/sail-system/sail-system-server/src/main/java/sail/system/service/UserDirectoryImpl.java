package sail.system.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import sail.system.api.UserDirectory;
import sail.system.mapper.SysUserMapper;

/** 系统用户契约的本地实现。 */
@Service
@RequiredArgsConstructor
public class UserDirectoryImpl implements UserDirectory {
    private final SysUserMapper users;

    @Override
    public boolean isActive(long id) {
        // 返回用户真实启用状态，未知用户不能当作有效接收人。
        var user = users.selectById(id);
        return user != null && user.getStatus() == 1;
    }

    @Override
    public boolean areActive(java.util.Collection<Long> ids) {
        // 批量校验避免为每名接收人分别查询数据库。
        if (ids.isEmpty()) return true;
        return users.selectCount(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers
                                .<sail.system.entity.SysUser>lambdaQuery()
                                .in(sail.system.entity.SysUser::getId, ids)
                                .eq(sail.system.entity.SysUser::getStatus, 1))
                == ids.stream().distinct().count();
    }
}
