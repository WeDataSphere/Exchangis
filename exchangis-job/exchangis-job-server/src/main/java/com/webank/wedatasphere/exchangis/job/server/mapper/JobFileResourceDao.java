package com.webank.wedatasphere.exchangis.job.server.mapper;

import com.webank.wedatasphere.exchangis.job.server.domain.JobFileResource;

/**
 * File source BML resource dao (mirrors {@code JobTransformProcessorDao} pattern).
 *
 * <p>MyBatis auto-scans {@code mapper/impl/*.xml} and the {@code job.server.mapper} base package
 * (see {@code dss-exchangis-server.properties}), so no manual registration is needed.
 *
 * <p>文件 source BML 资源元数据 DAO（仿 {@code JobTransformProcessorDao} 模式）。
 */
public interface JobFileResourceDao {

    /**
     * Insert one entity (落库一条)
     *
     * @param entity entity
     * @return auto-generated id
     */
    Long insert(JobFileResource entity);

    /**
     * Get by job id (按作业ID查)
     *
     * @param jobId job id
     * @return entity
     */
    JobFileResource getByJobId(Long jobId);

    /**
     * Get by BML resource id (按 BML resourceId 查)
     *
     * @param bmlResourceId bml resource id
     * @return entity
     */
    JobFileResource getByResourceId(String bmlResourceId);

    /**
     * Update BML version (重新上传时更新 BML version)
     *
     * @param entity entity
     */
    void updateVersion(JobFileResource entity);

    /**
     * Delete by job id (作业删除联动)
     *
     * @param jobId job id
     */
    void deleteByJobId(Long jobId);
}
