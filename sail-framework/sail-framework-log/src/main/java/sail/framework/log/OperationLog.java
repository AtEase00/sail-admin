package sail.framework.log;

import java.lang.annotation.*;

/** 关键业务操作审计标记，禁止将秘密写入描述。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String value();

    sail.common.core.OperationType type() default sail.common.core.OperationType.AUTO;
}
