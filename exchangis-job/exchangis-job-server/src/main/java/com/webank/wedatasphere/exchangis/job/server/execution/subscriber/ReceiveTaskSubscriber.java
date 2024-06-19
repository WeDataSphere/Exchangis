package com.webank.wedatasphere.exchangis.job.server.execution.subscriber;

import com.webank.wedatasphere.exchangis.job.launcher.domain.LaunchableExchangisTask;
import com.webank.wedatasphere.exchangis.job.launcher.domain.LaunchedExchangisTask;
import com.webank.wedatasphere.exchangis.job.server.exception.ExchangisTaskObserverException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Receive the task in '
 */
@Component
public class ReceiveTaskSubscriber extends AbstractTaskObserver<LaunchedExchangisTask> {


    @Override
    protected List<LaunchedExchangisTask> onPublish(int batchSize) throws ExchangisTaskObserverException {
        return new ArrayList<>();
    }



    @Override
    public int subscribe(List<LaunchedExchangisTask> publishedTasks,
                         List<LaunchedExchangisTask> unsubscribedTasks) throws ExchangisTaskObserverException {
        return 0;
    }
}
