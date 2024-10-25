package com.webank.wedatasphere.exchangis.job.server.execution.generator;

import com.webank.wedatasphere.exchangis.datasource.core.service.MetadataInfoService;
import com.webank.wedatasphere.exchangis.job.listener.JobLogListener;

/**
 * Generator context
 */
public interface TaskGeneratorContext {

    /**
     * Job Log listener
     * @return
     */
    JobLogListener getJobLogListener();

    /**
     * Metadata info service
     * @return
     */
    MetadataInfoService getMetadataInfoService();
}
