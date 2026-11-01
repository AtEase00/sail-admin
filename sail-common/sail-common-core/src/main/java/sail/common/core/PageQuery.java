package sail.common.core;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 有界分页参数。 */
public record PageQuery(@Min(1) long page, @Min(1) @Max(200) long size) {}
