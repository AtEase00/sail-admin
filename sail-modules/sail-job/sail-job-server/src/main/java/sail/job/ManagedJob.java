package sail.job;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

/** 定时触发和手工触发使用同一个 JobKey，集群中也禁止重叠执行。 */
@DisallowConcurrentExecution
public class ManagedJob implements Job {
    @Autowired private JobRunner runner;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            runner.execute(context);
        } catch (Exception e) {
            throw new JobExecutionException(e, false);
        }
    }
}
