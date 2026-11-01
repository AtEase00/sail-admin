package sail.common.core;

import cn.hutool.core.util.StrUtil;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** 列表分页与查询条件，业务服务明确绑定可检索字段，不接收客户端 SQL 字段名。 */
@Getter
@Setter
public class ListQuery {
    /** 目录编号精确查询，避免前端在完整数据上二次筛选。 */
    @Min(1)
    private Long id;

    /** 角色备注的独立检索条件。 */
    @Size(max = 255)
    private String remark;

    /** 存储、支付渠道等固定类型的精确检索条件。 */
    @Size(max = 32)
    private String type;

    /** 当前页，从第一页开始。 */
    @Min(1)
    private long page = 1;

    /** 每页数量限制与分页插件一致。 */
    @Min(1)
    @Max(200)
    private long size = 20;

    /** 文本搜索值始终通过参数绑定参与查询。 */
    @Size(max = 200)
    private String keyword;

    /** 状态值由各业务服务转换为其实际字段类型。 */
    @Size(max = 32)
    private String status;

    /** 创建时间下界，使用 ISO 本地时间，不擅自转换为其他时区。 */
    @Size(max = 32)
    private String createdFrom;

    /** 创建时间上界，可只提供一侧条件。 */
    @Size(max = 32)
    private String createdTo;

    /** 审计请求方式，作为精确 SQL 条件。 */
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS|^$")
    private String requestMethod;

    /** 审计操作类型来自受控业务枚举。 */
    @Size(max = 32)
    private String operationType;

    /** 字典数据所属类型，用于服务端精确过滤。 */
    @Size(max = 64)
    private String typeCode;

    /** 解析明确的查询时间并校验区间，非法日期直接返回业务错误。 */
    public TimeRange createdRange() {
        try {
            var from = StrUtil.isBlank(createdFrom) ? null : LocalDateTime.parse(createdFrom);
            var to = StrUtil.isBlank(createdTo) ? null : LocalDateTime.parse(createdTo);
            if (from != null && to != null && from.isAfter(to))
                throw new BusinessException(400, "开始时间不能晚于结束时间");
            return new TimeRange(from, to);
        } catch (DateTimeParseException exception) {
            // 客户端输入的格式错误不作为服务器异常返回。
            throw new BusinessException(400, "时间格式应为 yyyy-MM-dd'T'HH:mm[:ss]");
        }
    }

    /** 已校验的时间区间，由所有分页服务共用。 */
    public record TimeRange(LocalDateTime from, LocalDateTime to) {}

    /** 将明确的布尔查询值转换为字段类型，非法值直接提示，不默认为 false。 */
    public Boolean booleanStatus() {
        if (StrUtil.isBlank(status)) return null;
        if ("true".equals(status)) return Boolean.TRUE;
        if ("false".equals(status)) return Boolean.FALSE;
        throw new BusinessException(400, "状态仅支持 true 或 false");
    }

    /** 启停状态只接受数据库定义的 0 和 1，不使用任意数字替代。 */
    public Integer enabledStatus() {
        if (StrUtil.isBlank(status)) return null;
        if ("1".equals(status)) return 1;
        if ("0".equals(status)) return 0;
        throw new BusinessException(400, "状态仅支持 0 或 1");
    }
}
