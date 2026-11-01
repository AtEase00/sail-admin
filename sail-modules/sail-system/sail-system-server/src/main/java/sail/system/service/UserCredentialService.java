package sail.system.service;

import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.framework.security.PasswordConstants;
import sail.framework.security.PasswordPolicy;
import sail.system.entity.SysUserCredential;
import sail.system.mapper.SysUserCredentialMapper;

/** 密码凭据生命周期管理，普通设密生成新盐，初始管理员可使用环境专属盐。 */
@Service
@RequiredArgsConstructor
public class UserCredentialService {
    private final SysUserCredentialMapper credentials;
    private final PasswordEncoder passwords;

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(long userId, String password) {
        // 与用户资料共用事务，凭据写入失败时不留下不能登录的半成品账号。
        credentials.insert(encode(userId, password, null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createInitialAdministrator(long userId, String password, String salt) {
        // 仅初始化入口接受环境配置的独立盐，普通账号和后续改密不复用此盐。
        credentials.insert(encode(userId, password, salt));
    }

    public boolean matches(long userId, String password) {
        // 登录只查有效凭据，不再从用户资料读取密码材料。
        return matches(find(userId), password);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void change(long userId, String oldPassword, String newPassword) {
        // 先验证当前凭据，再以旧哈希作为更新条件，阻止并发改密覆盖刚更新的密码。
        SysUserCredential current = find(userId);
        if (!matches(current, oldPassword)) throw new BusinessException("原密码错误");
        replace(current, newPassword);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(long userId, String password) {
        // 管理员重置也必须存在有效凭据，不以其他账号的值或默认值替代。
        SysUserCredential current = find(userId);
        if (current == null) throw new BusinessException("用户密码凭据不存在，请检查数据迁移");
        replace(current, password);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(long userId) {
        // 与账号删除共用事务，凭据使用自己的主键作为逻辑删除标记。
        credentials.delete(
                Wrappers.<SysUserCredential>lambdaQuery().eq(SysUserCredential::getUserId, userId));
    }

    private SysUserCredential find(long userId) {
        // MyBatis-Plus 自动过滤 deleted 非零的历史凭据。
        return credentials.selectOne(
                Wrappers.<SysUserCredential>lambdaQuery().eq(SysUserCredential::getUserId, userId));
    }

    private boolean matches(SysUserCredential credential, String password) {
        // 盐列必须与标准 BCrypt 编码一致；缺失或不一致的凭据不能通过认证。
        if (credential == null
                || StrUtil.isBlank(credential.getPasswordHash())
                || StrUtil.length(credential.getPasswordSalt())
                        != PasswordConstants.BCRYPT_SALT_PREFIX_LENGTH
                || !credential.getPasswordHash().startsWith(credential.getPasswordSalt()))
            return false;
        return passwords.matches(password, credential.getPasswordHash());
    }

    private SysUserCredential encode(long userId, String password, String initialSalt) {
        // 开发初始管理员可显式指定标准 BCrypt 盐；其余入口使用标准随机编码器。
        PasswordPolicy.validate(password);
        if (StrUtil.isNotBlank(initialSalt)
                && !initialSalt.matches(PasswordConstants.BCRYPT_SALT_PATTERN))
            throw new BusinessException("初始管理员盐必须为成本12的标准BCrypt盐前缀");
        String encoded =
                StrUtil.isBlank(initialSalt)
                        ? passwords.encode(password)
                        : BCrypt.hashpw(password, initialSalt);
        SysUserCredential credential = new SysUserCredential();
        credential.setUserId(userId);
        credential.setPasswordHash(encoded);
        credential.setPasswordSalt(
                encoded.substring(0, PasswordConstants.BCRYPT_SALT_PREFIX_LENGTH));
        return credential;
    }

    private void replace(SysUserCredential current, String password) {
        // 密码哈希和新盐在同一条更新中替换，任何并发变更均明确返回冲突。
        SysUserCredential next = encode(current.getUserId(), password, null);
        int updated =
                credentials.update(
                        null,
                        Wrappers.<SysUserCredential>lambdaUpdate()
                                .eq(SysUserCredential::getId, current.getId())
                                .eq(SysUserCredential::getPasswordHash, current.getPasswordHash())
                                .set(SysUserCredential::getPasswordHash, next.getPasswordHash())
                                .set(SysUserCredential::getPasswordSalt, next.getPasswordSalt())
                                .set(
                                        SysUserCredential::getUpdatedAt,
                                        java.time.LocalDateTime.now()));
        if (updated != 1) throw new BusinessException(409, "密码凭据已变更，请重新操作");
    }
}
