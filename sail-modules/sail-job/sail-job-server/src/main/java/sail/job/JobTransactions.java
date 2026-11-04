package sail.job;

import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import java.util.function.Consumer;

/** 独立事务只作用于任务运行，不替换其他业务模块的默认 TransactionTemplate。 */
@Component
public class JobTransactions {
    private final TransactionTemplate template;

    public JobTransactions(PlatformTransactionManager manager) {
        template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(TransactionCallback<T> callback) {
        return template.execute(callback);
    }

    public void executeWithoutResult(Consumer<TransactionStatus> callback) {
        template.executeWithoutResult(callback);
    }
}
