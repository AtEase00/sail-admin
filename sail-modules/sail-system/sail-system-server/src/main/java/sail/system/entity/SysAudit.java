package sail.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.EqualsAndHashCode;

import sail.framework.mybatis.BaseEntity;

/** SysAudit 数据库映射，仅供本模块使用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_audit")
public class SysAudit extends BaseEntity {
    private Long userId;
    private String action;
    private Boolean success;
    private String traceId;
    private String operationType;
    private String requestMethod;
    private String requestUrl;
    private String requestParams;
    private String requestBody;
    private String responseBody;
    private Integer httpStatus;
    private Integer responseCode;
    private Long durationMs;
    private String errorMessage;
}
