package sail.system.dto;

import jakarta.validation.constraints.*;

import sail.framework.security.PasswordConstants;

import java.util.List;

/** 系统管理请求模型，禁止直接绑定数据库实体。 */
public final class SystemRequests {
    private SystemRequests() {
        /* 请求模型命名空间。 */
    }

    /** 登录请求。 */
    public record Login(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {}

    /** 用户创建请求。 */
    public record UserCreate(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(min = PasswordConstants.MIN_PASSWORD_LENGTH) String password,
            @NotBlank @Size(max = 64) String nickname,
            @Positive Long deptId,
            @NotNull @Min(0) @Max(1) Integer status) {}

    /** 用户资料更新，不接收密码。 */
    public record UserUpdate(
            @NotBlank @Size(max = 64) String nickname,
            @Positive Long deptId,
            @NotNull @Min(0) @Max(1) Integer status) {}

    /** 修改自身密码时校验原密码。 */
    public record Password(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = PasswordConstants.MIN_PASSWORD_LENGTH) String newPassword) {}

    /** 管理员重置密码。 */
    public record ResetPassword(
            @NotBlank @Size(min = PasswordConstants.MIN_PASSWORD_LENGTH) String password) {}

    /** 批量授权，限制数量避免超大 SQL。 */
    public record Assignment(@NotNull @Size(max = 500) List<@NotNull @Positive Long> ids) {}

    /** 角色与数据范围。 */
    public record Role(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,
            @NotNull @Min(0) @Max(1) Integer status,
            @NotNull @Min(1) @Max(5) Integer dataScope,
            @Size(max = 255) String remark) {}

    /** 字典类型与稳定类型编码。 */
    public record DictionaryType(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String code,
            @NotBlank @Size(max = 64) String name,
            @NotNull @Min(0) @Max(1) Integer status,
            @Size(max = 255) String remark) {}

    /** 菜单、目录与按钮权限。 */
    public record Menu(
            @Positive Long parentId,
            @NotBlank @Size(max = 64) String name,
            @Size(max = 128) String permission,
            @Size(max = 200) String path,
            @NotBlank @Pattern(regexp = "DIRECTORY|MENU|BUTTON") String type,
            @Size(max = 64) @Pattern(regexp = "[a-z0-9-]*") String icon,
            @NotNull Integer sortOrder,
            @NotNull @Min(0) @Max(1) Integer status) {}

    /** 部门树节点。 */
    public record Dept(
            @Positive Long parentId,
            @NotBlank @Size(max = 64) String name,
            @NotNull Integer sortOrder,
            @NotNull @Min(0) @Max(1) Integer status) {}

    /** 岗位资料。 */
    public record Post(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 64) String code,
            @NotNull @Min(0) @Max(1) Integer status) {}

    /** 字典项。 */
    public record Dictionary(
            @NotBlank @Size(max = 64) String typeCode,
            @NotBlank @Size(max = 100) String label,
            @NotBlank @Size(max = 100) String value,
            @NotNull Integer sortOrder,
            @NotNull @Min(0) @Max(1) Integer status,
            @Pattern(regexp = "default|success|warning|error|processing") String color,
            @Size(max = 255) String remark,
            Boolean isDefault) {}

    /** 非秘密系统参数，秘密由专用配置接口管理。 */
    public record Config(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9_.-]{1,99}") String configKey,
            @NotNull @Size(max = 2000) String configValue,
            @Size(max = 300) String description) {}
}
