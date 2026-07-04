package com.webank.wedatasphere.exchangis.job.server.builder.transform.handlers;

import com.webank.wedatasphere.exchangis.job.builder.ExchangisJobBuilderContext;
import com.webank.wedatasphere.exchangis.job.domain.SubExchangisJob;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParamDefine;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParamSet;
import com.webank.wedatasphere.exchangis.job.domain.params.JobParams;
import com.webank.wedatasphere.exchangis.job.server.builder.JobParamConstraints;
import org.apache.linkis.common.conf.CommonVars;
import org.apache.linkis.common.exception.ErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * File source handler for the datax engine (DataX 引擎的文件 source 处理器)
 *
 * <p>Registration: auto-discovered via reflection by {@code GenericExchangisTransformJobBuilder.initHandlers()}
 * (keyed by {@link #dataSourceType()} = "file"), no manual registration needed.
 *
 * <p>Architecture ② / M5: the file source does NOT go through datasource management. This handler
 * does NOT call {@code DataSourceService}; it only extracts the BML reference from the source
 * params and stashes it for the engine builder to materialize as an {@code EngineBmlResource}.
 *
 * <p>架构②/M5：文件 source 不走数据源管理流程。本 handler 不调用 DataSourceService，
 * 仅从 source 参数中提取 BML 引用并暂存，供引擎 builder 物化为 EngineBmlResource。
 *
 * <h2>Param convention (参数约定)</h2>
 * <ul>
 *   <li>BML reference params are namespaced with the {@code __file_bml_} prefix
 *       (e.g. {@code __file_bml_resource_id}). They are read with
 *       {@link JobParams#define(String)} and stay in the source param set; txtfilereader ignores
 *       these unknown keys, so no manual get+remove is needed.</li>
 *   <li>txtfilereader params ({@code path}/{@code encoding}/{@code delimiter}/{@code nullFormat})
 *       are real reader keys. {@code path} is set to {@link #FILE_BASE_PATH} (the subdirectory under
 *       the EC workdir where the BML resource is downloaded); the others are sent by the frontend
 *       from the parse result.</li>
 * </ul>
 *
 * <p>BML 引用参数以 {@code __file_bml_} 前缀命名空间隔离，用 {@link JobParams#define(String)} 读取，
 * 保留在 source 参数集中（txtfilereader 忽略未知键，无需 get+remove）。txtfilereader 参数
 * （path/encoding/delimiter/nullFormat）为真实 reader 键，path 设为 FILE_BASE_PATH（EC 工作目录下 BML 资源下载到的子目录）。
 *
 * <h2>BML injection adaptation (BML 注入适配)</h2>
 * The design doc describes writing a plain-JSON {@code wds.linkis.engineconn.datax.bml.resources} job
 * property in the handler. This open-source codebase has no {@code getJobProps()} on
 * {@link SubExchangisJob}, but it ALREADY serializes {@code engineJob.getResources()} into
 * {@code wds.linkis.engineconn.datax.bml.resources} at launch time
 * (see {@code ExchangisLauncherConfiguration.LAUNCHER_LINKIS_RESOURCES} +
 * {@code DataxEngineConnLaunchBuilder.getBmlResources}). So the handler stashes the BML ref into
 * {@link SubExchangisJob#getJobParams()} and {@code DataxExchangisEngineJobBuilder} materializes it
 * as an {@code EngineBmlResource} — the same pattern as the existing {@code settingProcessorInfo}.
 *
 * <p>设计文档描述在 handler 中写入明文 JSON 作业属性。本仓库的 {@link SubExchangisJob} 无
 * {@code getJobProps()}，但已有机制：launcher 把 {@code engineJob.getResources()} 序列化为
 * {@code wds.linkis.engineconn.datax.bml.resources}。故 handler 将 BML 引用暂存到 jobParams，
 * 由 {@code DataxExchangisEngineJobBuilder} 物化为 {@code EngineBmlResource}。
 */
public class FileDataxSubExchangisJobHandler extends AuthEnabledSubExchangisJobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FileDataxSubExchangisJobHandler.class);

    /**
     * Base path (subdirectory under the EC workdir) where file source BML resources are downloaded
     * and from which txtfilereader reads. Mirrors the {@code PROCESSOR_BASE_PATH} pattern in
     * {@code DataxExchangisEngineJobBuilder.settingProcessorInfo}. Default "fileSets".
     *
     * <p>The engine builder downloads the BML resource to {@code <EC workdir>/<FILE_BASE_PATH>/<name>}
     * (it prefixes the resource name with this base path), and txtfilereader's {@code path} param
     * is set to this value so it reads from that subdirectory.
     *
     * <p>EC 工作目录下文件 source BML 资源下载到的子目录，txtfilereader 从此目录读取。
     * 仿 {@code DataxExchangisEngineJobBuilder.settingProcessorInfo} 的 {@code PROCESSOR_BASE_PATH} 模式，
     * 默认 "fileSets"。引擎 builder 将 BML 资源下载到 {@code <EC workdir>/<FILE_BASE_PATH>/<name>}
     * （给资源名加此前缀），txtfilereader 的 path 参数设为此值，从该子目录读取。
     */
    public static final CommonVars<String> FILE_BASE_PATH =
            CommonVars.apply("wds.exchangis.file-source.base-path", "fileSets");

    /**
     * Job param key used to stash the file BML reference for the engine builder.
     * 暂存文件 BML 引用供引擎 builder 使用的 job param key。
     */
    public static final String FILE_BML_RESOURCE_KEY = "__file_bml_resource";

    /**
     * BML reference source params (namespaced with __file_bml_ prefix).
     * BML 引用 source 参数（以 __file_bml_ 前缀命名空间隔离）。
     */
    public static final String PARAM_BML_RESOURCE_ID = "__file_bml_resource_id";
    public static final String PARAM_BML_VERSION = "__file_bml_version";
    public static final String PARAM_BML_OWNER = "__file_bml_owner";
    public static final String PARAM_BML_NAME = "__file_bml_name";

    private static final JobParamDefine<String> BML_RESOURCE_ID = JobParams.define(PARAM_BML_RESOURCE_ID);
    private static final JobParamDefine<String> BML_VERSION = JobParams.define(PARAM_BML_VERSION);
    private static final JobParamDefine<String> BML_OWNER = JobParams.define(PARAM_BML_OWNER);
    private static final JobParamDefine<String> BML_NAME = JobParams.define(PARAM_BML_NAME);

    /**
     * txtfilereader params (real reader keys) / txtfilereader 参数（真实 reader 键）
     */
    private static final String PARAM_PATH = "path";
    private static final String PARAM_DELIMITER = "delimiter";
    private static final JobParamDefine<String> ENCODING = JobParams.define(JobParamConstraints.ENCODING);
    private static final JobParamDefine<String> DELIMITER = JobParams.define(PARAM_DELIMITER);
    private static final JobParamDefine<String> NULL_FORMAT = JobParams.define(JobParamConstraints.NULL_FORMAT);

    @Override
    public void handleJobSource(SubExchangisJob subExchangisJob, ExchangisJobBuilderContext ctx) throws ErrorException {
        JobParamSet paramSet = subExchangisJob.getRealmParams(SubExchangisJob.REALM_JOB_CONTENT_SOURCE);
        if (Objects.isNull(paramSet)) {
            return;
        }

        // 1. Read the BML reference from the namespaced __file_bml_ source params.
        //    They stay in the source param set (txtfilereader ignores the __file_bml_ keys).
        //    读取 BML 引用（__file_bml_ 前缀命名空间隔离，保留在 source 参数集中，txtfilereader 忽略）
        String resourceId = BML_RESOURCE_ID.getValue(paramSet);
        String version = BML_VERSION.getValue(paramSet);
        String owner = BML_OWNER.getValue(paramSet);
        String name = BML_NAME.getValue(paramSet);

        if (Objects.nonNull(resourceId) && Objects.nonNull(version)) {
            // 2. Stash into jobParams for the engine builder (暂存到 jobParams 供引擎 builder 使用)
            Map<String, String> bmlRef = new HashMap<>();
            bmlRef.put(PARAM_BML_RESOURCE_ID, resourceId);
            bmlRef.put(PARAM_BML_VERSION, version);
            bmlRef.put(PARAM_BML_OWNER, owner);
            bmlRef.put(PARAM_BML_NAME, name);
            subExchangisJob.getJobParams().put(FILE_BML_RESOURCE_KEY, bmlRef);
            LOG.info("File source BML reference stashed (文件 source BML 引用已暂存): name={}, resourceId={}, version={}",
                    name, resourceId, version);

            // 3. path = FILE_BASE_PATH. The engine builder downloads the BML resource to
            //    <EC workdir>/<FILE_BASE_PATH>/<name> (prefixing the resource name with FILE_BASE_PATH),
            //    and txtfilereader reads from this subdirectory. Mirrors PROCESSOR_BASE_PATH.
            //    path = FILE_BASE_PATH。引擎 builder 将 BML 资源下载到 <EC workdir>/<FILE_BASE_PATH>/<name>
            //    （给资源名加 FILE_BASE_PATH 前缀），txtfilereader 从此子目录读取。仿 PROCESSOR_BASE_PATH。
            paramSet.add(JobParams.newOne(PARAM_PATH, FILE_BASE_PATH.getValue()));
        } else {
            LOG.warn("File source handler: missing resourceId/version in source params (source 参数缺失 resourceId/version)");
        }

        // 4. Ensure the txtfilereader params are present in the output param set.
        //    确保 txtfilereader 参数（encoding/delimiter/nullFormat）存在于输出参数集。
        paramSet.addNonNull(ENCODING.get(paramSet));
        paramSet.addNonNull(DELIMITER.get(paramSet));
        paramSet.addNonNull(NULL_FORMAT.get(paramSet));
    }

    @Override
    public void handleJobSink(SubExchangisJob subExchangisJob, ExchangisJobBuilderContext ctx) throws ErrorException {
        // FILE is source-only; job validation rejects sink=file. (FILE 仅作 source，不实现 sink)
    }

    @Override
    public String dataSourceType() {
        return "file";
    }

    @Override
    public boolean acceptEngine(String engineType) {
        return "datax".equalsIgnoreCase(engineType);
    }
}
