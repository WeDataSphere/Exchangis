package com.webank.wedatasphere.exchangis.job.server.builder.transform.handlers;

import com.webank.wedatasphere.exchangis.job.domain.SubExchangisJob;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParam;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParamSet;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link FileDataxSubExchangisJobHandler}
 * ({@link FileDataxSubExchangisJobHandler} 单元测试).
 *
 * <p>Verifies the handler outputs txtfilereader params {@code path} and {@code skipHeader}
 * (default true) when the BML reference is present, and skips them when it is missing.
 * (验证 BML 引用齐全时输出 txtfilereader 参数 path 与 skipHeader（默认 true），缺失时不输出。)
 */
class FileDataxSubExchangisJobHandlerTest {

    private static final String PARAM_BML_RESOURCE_ID = "__file_bml_resource_id";
    private static final String PARAM_BML_VERSION = "__file_bml_version";
    private static final String PARAM_BML_OWNER = "__file_bml_owner";
    private static final String PARAM_BML_NAME = "__file_bml_name";

    @Test
    @DisplayName("handleJobSource outputs path and skipHeader=true when BML ref is present "
            + "(BML 引用齐全时输出 path 与 skipHeader=true)")
    void testHandleJobSourceAddsPathAndSkipHeader() {
        SubExchangisJob job = new SubExchangisJob();
        JobParamSet paramSet = new JobParamSet();
        paramSet.add(JobParams.newOne(PARAM_BML_RESOURCE_ID, "rid-123"));
        paramSet.add(JobParams.newOne(PARAM_BML_VERSION, "v1"));
        paramSet.add(JobParams.newOne(PARAM_BML_OWNER, "alice"));
        paramSet.add(JobParams.newOne(PARAM_BML_NAME, "data.csv"));
        job.addRealmParams(SubExchangisJob.REALM_JOB_CONTENT_SOURCE, paramSet);

        new FileDataxSubExchangisJobHandler().handleJobSource(job, null);

        JobParam<?> path = paramSet.get("path");
        assertNotNull(path, "path should be set when BML ref present (BML 引用齐全时应设置 path)");
        assertEquals("fileSets", path.getValue(),
                "path default = fileSets (path 默认 fileSets)");

        JobParam<?> skipHeader = paramSet.get("skipHeader");
        assertNotNull(skipHeader, "skipHeader should be set when BML ref present (BML 引用齐全时应设置 skipHeader)");
        assertEquals(Boolean.TRUE, skipHeader.getValue(),
                "skipHeader default = true (skipHeader 默认 true)");
    }

    @Test
    @DisplayName("handleJobSource skips path/skipHeader when BML ref is missing "
            + "(BML 引用缺失时不设置 path/skipHeader)")
    void testHandleJobSourceMissingBmlRefSkipsReaderParams() {
        SubExchangisJob job = new SubExchangisJob();
        JobParamSet paramSet = new JobParamSet();
        // No BML ref params (未提供 BML 引用参数)
        job.addRealmParams(SubExchangisJob.REALM_JOB_CONTENT_SOURCE, paramSet);

        new FileDataxSubExchangisJobHandler().handleJobSource(job, null);

        assertNull(paramSet.get("path"),
                "path should NOT be set without BML ref (无 BML 引用不应设置 path)");
        assertNull(paramSet.get("skipHeader"),
                "skipHeader should NOT be set without BML ref (无 BML 引用不应设置 skipHeader)");
    }

    @Test
    @DisplayName("handleJobSource is a no-op when source realm paramSet is absent "
            + "(source realm 参数集不存在时为空操作)")
    void testHandleJobSourceNoOpWhenParamSetAbsent() {
        SubExchangisJob job = new SubExchangisJob();
        // Do not add any realm paramSet (不添加任何 realm 参数集)
        new FileDataxSubExchangisJobHandler().handleJobSource(job, null);
        // No exception thrown is the assertion (无异常抛出即为通过)
    }
}
